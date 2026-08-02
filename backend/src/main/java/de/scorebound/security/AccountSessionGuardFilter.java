package de.scorebound.security;

import de.scorebound.identity.AccountRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
@Component
public class AccountSessionGuardFilter extends OncePerRequestFilter {

	private final AccountRepository accountRepository;

	public AccountSessionGuardFilter(AccountRepository accountRepository) {
		this.accountRepository = accountRepository;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
			FilterChain filterChain) throws ServletException, IOException {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || !(authentication.getPrincipal() instanceof ScoreboundPrincipal principal)) {
			filterChain.doFilter(request, response);
			return;
		}

		HttpSession session = request.getSession(false);
		if (session == null || isExpired(session) || isAccountInvalid(principal)) {
			invalidate(session);
			writeProblem(response, HttpServletResponse.SC_UNAUTHORIZED, "authentication_required");
			return;
		}

		if (principal.mustChangePassword() && !isPasswordChangeRequest(request)) {
			writeProblem(response, HttpServletResponse.SC_FORBIDDEN, "password_change_required");
			return;
		}

		filterChain.doFilter(request, response);
	}

	private boolean isExpired(HttpSession session) {
		Object expiresAt = session.getAttribute(SessionPolicy.EXPIRES_AT_ATTRIBUTE);
		return expiresAt instanceof Instant instant && !instant.isAfter(Instant.now());
	}

	private boolean isAccountInvalid(ScoreboundPrincipal principal) {
		return accountRepository.findById(principal.accountId())
				.map(account -> !account.isEnabled()
						|| !account.getPasswordHash().equals(principal.getPassword())
						|| !account.getRoles().equals(principal.roles()))
				.orElse(true);
	}

	private boolean isPasswordChangeRequest(HttpServletRequest request) {
		return (request.getRequestURI().equals("/api/v1/session")
				&& (request.getMethod().equals("GET") || request.getMethod().equals("DELETE")))
				|| (request.getRequestURI().equals("/api/v1/session/password")
				&& request.getMethod().equals("PUT"));
	}

	private static void invalidate(HttpSession session) {
		SecurityContextHolder.clearContext();
		if (session != null) {
			session.invalidate();
		}
	}

	private static void writeProblem(HttpServletResponse response, int status, String code) throws IOException {
		response.setStatus(status);
		response.setContentType("application/problem+json");
		response.getWriter().write("{\"status\":" + status + ",\"code\":\"" + code + "\"}");
	}
}
