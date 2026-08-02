package de.scorebound.security;

import com.jayway.jsonpath.JsonPath;
import de.scorebound.identity.Account;
import de.scorebound.identity.AccountRepository;
import de.scorebound.identity.AccountService;
import de.scorebound.identity.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityIntegrationTests {

	private static final String ADMIN_USERNAME = "admin";
	private static final String ADMIN_PASSWORD = "permanent-admin-password";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private AccountRepository accountRepository;

	@Autowired
	private AccountService accountService;

	@BeforeEach
	void setUpAdmin() {
		accountRepository.deleteAll();
		Account admin = accountService.createBootstrapAdmin(ADMIN_USERNAME, "temporary-admin-password");
		assertThat(admin.isMustChangePassword()).isTrue();
		accountService.changePassword(admin.getId(), "temporary-admin-password", ADMIN_PASSWORD);
	}

	@Test
	void protectsResourcesAndAuthenticatesCaseInsensitiveUsernames() throws Exception {
		mockMvc.perform(get("/api/v1/session"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("authentication_required"));

		mockMvc.perform(post("/api/v1/sessions")
					.contentType(APPLICATION_JSON)
					.content(loginJson("admin", "wrong-password", "Normal")))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("invalid_credentials"));

		login("admin", ADMIN_PASSWORD, "Display")
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("forbidden"));

		MvcResult login = login("ADMIN", ADMIN_PASSWORD, "Normal")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.roles[0]").value("Admin"))
				.andExpect(jsonPath("$.effectiveRoles[0]").value("Admin"))
				.andExpect(jsonPath("$.mustChangePassword").value(false))
				.andReturn();

		MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);
		assertThat(session).isNotNull();
		assertThat(session.getMaxInactiveInterval())
				.isEqualTo(Math.toIntExact(Duration.ofHours(12).toSeconds()));

		mockMvc.perform(get("/api/v1/session").session(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.csrfToken").isNotEmpty());
	}

	@Test
	void createsAccountsAndForcesTemporaryPasswordReplacement() throws Exception {
		MockHttpSession adminSession = sessionFrom(login(ADMIN_USERNAME, ADMIN_PASSWORD, "Normal")
				.andExpect(status().isOk())
				.andReturn());

		MvcResult creation = mockMvc.perform(post("/api/v1/accounts")
					.session(adminSession)
					.with(csrf())
					.contentType(APPLICATION_JSON)
					.content("""
							{
							  "username": "office-scorer",
							  "roles": ["Scorer", "Member"],
							  "preferredLocale": "de"
							}
							"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.mustChangePassword").value(true))
				.andExpect(jsonPath("$.temporaryPassword").isNotEmpty())
				.andReturn();

		String temporaryPassword = JsonPath.read(creation.getResponse().getContentAsString(),
				"$.temporaryPassword");
		String accountId = JsonPath.read(creation.getResponse().getContentAsString(), "$.id");
		MockHttpSession temporarySession = sessionFrom(login("office-scorer", temporaryPassword, "Normal")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.mustChangePassword").value(true))
				.andReturn());

		mockMvc.perform(get("/api/v1/accounts").session(temporarySession))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("password_change_required"));

		mockMvc.perform(put("/api/v1/session/password")
					.session(temporarySession)
					.with(csrf())
					.contentType(APPLICATION_JSON)
					.content("""
							{
							  "currentPassword": "%s",
							  "newPassword": "new-permanent-password"
							}
							""".formatted(temporaryPassword)))
				.andExpect(status().isNoContent());

		MockHttpSession permanentSession = sessionFrom(login("office-scorer",
				"new-permanent-password", "Normal")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.mustChangePassword").value(false))
				.andReturn());

		MvcResult reset = mockMvc.perform(post("/api/v1/accounts/{accountId}/temporary-password", accountId)
					.session(adminSession)
					.with(csrf()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.temporaryPassword").isNotEmpty())
				.andReturn();
		String resetPassword = JsonPath.read(reset.getResponse().getContentAsString(), "$.temporaryPassword");

		mockMvc.perform(get("/api/v1/session").session(permanentSession))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("authentication_required"));
		login("office-scorer", "new-permanent-password", "Normal")
				.andExpect(status().isUnauthorized());
		login("office-scorer", resetPassword, "Normal")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.mustChangePassword").value(true));
	}

	@Test
	void displayLoginRestrictsEffectiveRolesAndUsesLongSession() throws Exception {
		AccountService.CreatedAccount created = accountService.createWithTemporaryPassword(
				"wall-display", Set.of(Role.ADMIN, Role.DISPLAY), "en", null);
		accountService.changePassword(created.account().getId(), created.temporaryPassword(),
				"permanent-display-password");

		MvcResult login = login("wall-display", "permanent-display-password", "Display")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.roles.length()").value(2))
				.andExpect(jsonPath("$.effectiveRoles.length()").value(1))
				.andExpect(jsonPath("$.effectiveRoles[0]").value("Display"))
				.andReturn();

		MockHttpSession displaySession = sessionFrom(login);
		assertThat(displaySession.getMaxInactiveInterval())
				.isEqualTo(Math.toIntExact(Duration.ofDays(30).toSeconds()));

		mockMvc.perform(post("/api/v1/accounts")
					.session(displaySession)
					.with(csrf())
					.contentType(APPLICATION_JSON)
					.content("""
							{
							  "username": "forbidden-account",
							  "roles": ["Member"]
							}
							"""))
				.andExpect(status().isForbidden());
	}

	private org.springframework.test.web.servlet.ResultActions login(String username,
			String password, String mode) throws Exception {
		return mockMvc.perform(post("/api/v1/sessions")
				.contentType(APPLICATION_JSON)
				.content(loginJson(username, password, mode)));
	}

	private static String loginJson(String username, String password, String mode) {
		return """
				{
				  "username": "%s",
				  "password": "%s",
				  "mode": "%s"
				}
				""".formatted(username, password, mode);
	}

	private static MockHttpSession sessionFrom(MvcResult result) {
		return (MockHttpSession) result.getRequest().getSession(false);
	}
}
