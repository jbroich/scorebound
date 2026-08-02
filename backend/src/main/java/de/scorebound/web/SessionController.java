package de.scorebound.web;

import de.scorebound.identity.AccountService;
import de.scorebound.identity.Role;
import de.scorebound.security.ScoreboundPrincipal;
import de.scorebound.security.SessionMode;
import de.scorebound.security.SessionPolicy;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

@RestController
@RequestMapping("/api/v1")
public class SessionController {

	private final AuthenticationManager authenticationManager;
	private final SecurityContextRepository securityContextRepository;
	private final AccountService accountService;

	public SessionController(AuthenticationManager authenticationManager,
			SecurityContextRepository securityContextRepository, AccountService accountService) {
		this.authenticationManager = authenticationManager;
		this.securityContextRepository = securityContextRepository;
		this.accountService = accountService;
	}

	@PostMapping("/sessions")
	public ResponseEntity<?> login(@Valid @RequestBody LoginRequest loginRequest,
			HttpServletRequest request, HttpServletResponse response) {
		try {
			Authentication authenticated = authenticationManager.authenticate(
					UsernamePasswordAuthenticationToken.unauthenticated(
							loginRequest.username(), loginRequest.password()));
			ScoreboundPrincipal principal = (ScoreboundPrincipal) authenticated.getPrincipal();
			SessionMode mode = loginRequest.mode() == null ? SessionMode.NORMAL : loginRequest.mode();
			if (mode == SessionMode.DISPLAY && !principal.roles().contains(Role.DISPLAY)) {
				return problem(HttpStatus.FORBIDDEN, "forbidden");
			}

			HttpSession previousSession = request.getSession(false);
			if (previousSession != null) {
				previousSession.invalidate();
			}
			Authentication effectiveAuthentication = effectiveAuthentication(authenticated, principal, mode);
			SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
			securityContext.setAuthentication(effectiveAuthentication);
			SecurityContextHolder.setContext(securityContext);
			securityContextRepository.saveContext(securityContext, request, response);

			HttpSession session = request.getSession(false);
			Duration duration = mode == SessionMode.DISPLAY
					? SessionPolicy.DISPLAY_DURATION : SessionPolicy.NORMAL_DURATION;
			session.setMaxInactiveInterval(Math.toIntExact(duration.toSeconds()));
			session.setAttribute(SessionPolicy.MODE_ATTRIBUTE, mode);
			session.setAttribute(SessionPolicy.EXPIRES_AT_ATTRIBUTE, Instant.now().plus(duration));

			return ResponseEntity.ok(SessionResponse.from(principal, effectiveAuthentication, mode, null));
		} catch (AuthenticationException exception) {
			return problem(HttpStatus.UNAUTHORIZED, "invalid_credentials");
		}
	}

	@GetMapping("/session")
	public SessionResponse currentSession(Authentication authentication, CsrfToken csrfToken,
			HttpServletRequest request) {
		ScoreboundPrincipal principal = (ScoreboundPrincipal) authentication.getPrincipal();
		SessionMode mode = (SessionMode) request.getSession(false)
				.getAttribute(SessionPolicy.MODE_ATTRIBUTE);
		return SessionResponse.from(principal, authentication, mode, csrfToken.getToken());
	}

	@PutMapping("/session/password")
	public ResponseEntity<Void> changePassword(Authentication authentication,
			@Valid @RequestBody ChangePasswordRequest changePasswordRequest,
			HttpServletRequest request) {
		ScoreboundPrincipal principal = (ScoreboundPrincipal) authentication.getPrincipal();
		accountService.changePassword(principal.accountId(), changePasswordRequest.currentPassword(),
				changePasswordRequest.newPassword());
		HttpSession session = request.getSession(false);
		SecurityContextHolder.clearContext();
		if (session != null) {
			session.invalidate();
		}
		return ResponseEntity.noContent().build();
	}

	private static Authentication effectiveAuthentication(Authentication authenticated,
			ScoreboundPrincipal principal, SessionMode mode) {
		if (mode == SessionMode.DISPLAY) {
			return UsernamePasswordAuthenticationToken.authenticated(principal, null,
					Set.of(new SimpleGrantedAuthority("ROLE_DISPLAY")));
		}
		return authenticated;
	}

	private static ResponseEntity<ApiProblem> problem(HttpStatus status, String code) {
		return ResponseEntity.status(status)
				.contentType(MediaType.APPLICATION_PROBLEM_JSON)
				.body(new ApiProblem(status.value(), code));
	}

	public record LoginRequest(@NotBlank String username, @NotBlank String password, SessionMode mode) {
	}

	public record ChangePasswordRequest(@NotBlank String currentPassword,
			@NotBlank @Size(min = 12, max = 128) String newPassword) {
	}

	public record SessionResponse(String accountId, String username, Set<Role> roles,
			Set<Role> effectiveRoles, boolean mustChangePassword, String preferredLocale,
			SessionMode mode, String csrfToken) {

		static SessionResponse from(ScoreboundPrincipal principal, Authentication authentication,
				SessionMode mode, String csrfToken) {
			Set<Role> effectiveRoles = authentication.getAuthorities().stream()
					.filter(authority -> authority.getAuthority().startsWith("ROLE_"))
					.map(authority -> Role.valueOf(authority.getAuthority().substring("ROLE_".length())))
					.collect(java.util.stream.Collectors.toUnmodifiableSet());
			return new SessionResponse(principal.accountId().toString(), principal.getUsername(),
					principal.roles(), effectiveRoles, principal.mustChangePassword(),
					principal.preferredLocale(), mode, csrfToken);
		}
	}

	public record ApiProblem(int status, String code) {
	}
}
