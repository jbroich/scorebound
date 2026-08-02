package de.scorebound.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

@Configuration
@EnableMethodSecurity
public class SecurityConfiguration {

	@Bean
	PasswordEncoder passwordEncoder() {
		return PasswordEncoderFactories.createDelegatingPasswordEncoder();
	}

	@Bean
	AuthenticationManager authenticationManager(AccountUserDetailsService userDetailsService,
			PasswordEncoder passwordEncoder) {
		DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
		provider.setPasswordEncoder(passwordEncoder);
		return new ProviderManager(provider);
	}

	@Bean
	SecurityContextRepository securityContextRepository() {
		return new HttpSessionSecurityContextRepository();
	}

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http,
			SecurityContextRepository securityContextRepository,
			AccountSessionGuardFilter accountSessionGuardFilter) throws Exception {
		http
				.securityContext(context -> context.securityContextRepository(securityContextRepository))
				.csrf(csrf -> csrf
						.csrfTokenRepository(new HttpSessionCsrfTokenRepository())
						.ignoringRequestMatchers(PathPatternRequestMatcher.pathPattern(
								HttpMethod.POST, "/api/v1/sessions")))
				.sessionManagement(session -> session
						.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
						.sessionFixation(fixation -> fixation.changeSessionId()))
				.authorizeHttpRequests(authorize -> authorize
						.requestMatchers(HttpMethod.POST, "/api/v1/sessions").permitAll()
						.requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()
						.anyRequest().authenticated())
				.exceptionHandling(errors -> errors
						.authenticationEntryPoint((request, response, exception) -> {
							response.setStatus(401);
							response.setContentType("application/problem+json");
							response.getWriter().write("{\"status\":401,\"code\":\"authentication_required\"}");
						})
						.accessDeniedHandler((request, response, exception) -> {
							response.setStatus(403);
							response.setContentType("application/problem+json");
							response.getWriter().write("{\"status\":403,\"code\":\"forbidden\"}");
						}))
				.logout(logout -> logout
						.logoutRequestMatcher(PathPatternRequestMatcher.pathPattern(
								HttpMethod.DELETE, "/api/v1/session"))
						.logoutSuccessHandler((request, response, authentication) -> response.setStatus(204))
						.invalidateHttpSession(true)
						.clearAuthentication(true)
						.deleteCookies("JSESSIONID"))
				.formLogin(form -> form.disable())
				.httpBasic(basic -> basic.disable())
				.addFilterBefore(accountSessionGuardFilter, AuthorizationFilter.class);

		return http.build();
	}
}
