package de.scorebound.teams;

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

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.http.MediaType.IMAGE_PNG;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class TeamMemberIntegrationTests {

	private static final String ADMIN_PASSWORD = "permanent-admin-password";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private AccountRepository accountRepository;

	@Autowired
	private AccountService accountService;

	@Autowired
	private TeamRepository teamRepository;

	@Autowired
	private MemberRepository memberRepository;

	@Autowired
	private MembershipRepository membershipRepository;

	@Autowired
	private TeamImageRepository teamImageRepository;

	private MockHttpSession adminSession;

	@BeforeEach
	void setUp() throws Exception {
		teamImageRepository.deleteAll();
		accountRepository.deleteAll();
		membershipRepository.deleteAll();
		memberRepository.deleteAll();
		teamRepository.deleteAll();
		Account admin = accountService.createBootstrapAdmin("admin", "temporary-admin-password");
		accountService.changePassword(admin.getId(), "temporary-admin-password", ADMIN_PASSWORD);
		adminSession = sessionFrom(login("admin", ADMIN_PASSWORD).andReturn());
	}

	@Test
	void managesTeamsAndPreservesMembershipHistory() throws Exception {
		String redTeamId = createTeam("Red Comets", "RED", "#e5484d");
		String blueTeamId = createTeam("Blue Orbit", "BLU", "#3366FF");

		mockMvc.perform(post("/api/v1/teams")
					.session(adminSession).with(csrf())
					.contentType(APPLICATION_JSON)
					.content("""
							{"name":"red comets","shortName":"OTHER","color":"#FFFFFF"}
							"""))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("active_team_name_conflict"));

		MvcResult memberCreation = mockMvc.perform(post("/api/v1/members")
					.session(adminSession).with(csrf())
					.contentType(APPLICATION_JSON)
					.content("""
							{
							  "displayName":"Ada",
							  "firstName":"Ada",
							  "lastName":"Lovelace",
							  "teamId":"%s"
							}
							""".formatted(redTeamId)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.activeTeamId").value(redTeamId))
				.andExpect(jsonPath("$.memberships.length()").value(1))
				.andReturn();
		String memberId = JsonPath.read(memberCreation.getResponse().getContentAsString(), "$.id");

		mockMvc.perform(post("/api/v1/members/{memberId}/team-changes", memberId)
					.session(adminSession).with(csrf())
					.contentType(APPLICATION_JSON)
					.content("""
							{"teamId":"%s"}
							""".formatted(blueTeamId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.activeTeamId").value(blueTeamId))
				.andExpect(jsonPath("$.memberships.length()").value(2))
				.andExpect(jsonPath("$.memberships[0].teamId").value(blueTeamId))
				.andExpect(jsonPath("$.memberships[0].validUntil").doesNotExist())
				.andExpect(jsonPath("$.memberships[1].teamId").value(redTeamId))
				.andExpect(jsonPath("$.memberships[1].validUntil").isNotEmpty());

		mockMvc.perform(get("/api/v1/members/{memberId}", memberId).session(adminSession))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.memberships.length()").value(2));

		mockMvc.perform(patch("/api/v1/members/{memberId}", memberId)
					.session(adminSession).with(csrf())
					.contentType(APPLICATION_JSON)
					.content("""
							{"displayName":"Ada L.","firstName":"","active":false}
							"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.displayName").value("Ada L."))
				.andExpect(jsonPath("$.firstName").doesNotExist())
				.andExpect(jsonPath("$.active").value(false));

		mockMvc.perform(get("/api/v1/members").session(adminSession))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(0));
		mockMvc.perform(get("/api/v1/members?includeInactive=true").session(adminSession))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalElements").value(1));
	}

	@Test
	void enforcesAuthorizationStatusAndStrictInput() throws Exception {
		String teamId = createTeam("Active Team", "ACT", "#123456");
		AccountService.CreatedAccount created = accountService.createWithTemporaryPassword(
				"member-viewer", Set.of(Role.MEMBER), "en", null);
		accountService.changePassword(created.account().getId(), created.temporaryPassword(),
				"permanent-member-password");
		MockHttpSession memberSession = sessionFrom(login("member-viewer",
				"permanent-member-password").andReturn());

		mockMvc.perform(get("/api/v1/teams").session(memberSession))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].id").value(teamId));

		mockMvc.perform(post("/api/v1/teams")
					.session(memberSession).with(csrf())
					.contentType(APPLICATION_JSON)
					.content("""
							{"name":"Forbidden","shortName":"NO","color":"#FFFFFF"}
							"""))
				.andExpect(status().isForbidden());

		mockMvc.perform(patch("/api/v1/teams/{teamId}", teamId)
					.session(adminSession).with(csrf())
					.contentType(APPLICATION_JSON)
					.content("""
							{"active":false,"unknownField":true}
							"""))
				.andExpect(status().isBadRequest());

		mockMvc.perform(patch("/api/v1/teams/{teamId}", teamId)
					.session(adminSession).with(csrf())
					.contentType(APPLICATION_JSON)
					.content("""
							{"active":false}
							"""))
				.andExpect(status().isOk());

		mockMvc.perform(post("/api/v1/members")
					.session(adminSession).with(csrf())
					.contentType(APPLICATION_JSON)
					.content("""
							{"displayName":"No Team","teamId":"%s"}
							""".formatted(teamId)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("validation_failed"));

		mockMvc.perform(get("/api/v1/members?includeInactive=true").session(memberSession))
				.andExpect(status().isForbidden());
	}

	@Test
	void managesValidatedImagesAndOptionalAccountLinks() throws Exception {
		String teamId = createTeam("Image Team", "IMG", "#AABBCC");
		MvcResult memberCreation = mockMvc.perform(post("/api/v1/members")
					.session(adminSession).with(csrf())
					.contentType(APPLICATION_JSON)
					.content("""
							{"displayName":"Grace","teamId":"%s"}
							""".formatted(teamId)))
				.andExpect(status().isCreated())
				.andReturn();
		String memberId = JsonPath.read(memberCreation.getResponse().getContentAsString(), "$.id");

		byte[] pngHeader = new byte[] {
				(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a
		};
		mockMvc.perform(put("/api/v1/teams/{teamId}/image", teamId)
					.session(adminSession).with(csrf())
					.contentType(IMAGE_PNG)
					.content(new byte[] { 1, 2, 3 }))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("validation_failed"));

		mockMvc.perform(put("/api/v1/teams/{teamId}/image", teamId)
					.session(adminSession).with(csrf())
					.contentType(IMAGE_PNG)
					.content(pngHeader))
				.andExpect(status().isNoContent());

		mockMvc.perform(get("/api/v1/teams/{teamId}", teamId).session(adminSession))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.imageUrl").value("/api/v1/teams/" + teamId + "/image"));
		mockMvc.perform(get("/api/v1/teams/{teamId}/image", teamId).session(adminSession))
				.andExpect(status().isOk())
				.andExpect(header().string("Content-Type", "image/png"))
				.andExpect(content().bytes(pngHeader));

		AccountService.CreatedAccount first = accountService.createWithTemporaryPassword(
				"linked-member", Set.of(Role.MEMBER), "en", null);
		AccountService.CreatedAccount second = accountService.createWithTemporaryPassword(
				"other-member", Set.of(Role.MEMBER), "en", null);

		mockMvc.perform(patch("/api/v1/accounts/{accountId}", first.account().getId())
					.session(adminSession).with(csrf())
					.contentType(APPLICATION_JSON)
					.content("""
							{"memberId":"%s"}
							""".formatted(memberId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.memberId").value(memberId));

		mockMvc.perform(patch("/api/v1/accounts/{accountId}", second.account().getId())
					.session(adminSession).with(csrf())
					.contentType(APPLICATION_JSON)
					.content("""
							{"memberId":"%s"}
							""".formatted(memberId)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("member_already_linked"));

		assertThat(accountRepository.findById(first.account().getId()).orElseThrow().getMemberId())
				.hasToString(memberId);

		mockMvc.perform(patch("/api/v1/accounts/{accountId}", first.account().getId())
					.session(adminSession).with(csrf())
					.contentType(APPLICATION_JSON)
					.content("""
							{"memberId":null}
							"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.memberId").doesNotExist());
	}

	private String createTeam(String name, String shortName, String color) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/teams")
					.session(adminSession).with(csrf())
					.contentType(APPLICATION_JSON)
					.content("""
							{"name":"%s","shortName":"%s","color":"%s"}
							""".formatted(name, shortName, color)))
				.andExpect(status().isCreated())
				.andReturn();
		return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
	}

	private org.springframework.test.web.servlet.ResultActions login(String username,
			String password) throws Exception {
		return mockMvc.perform(post("/api/v1/sessions")
				.contentType(APPLICATION_JSON)
				.content("""
						{"username":"%s","password":"%s","mode":"Normal"}
						""".formatted(username, password)));
	}

	private static MockHttpSession sessionFrom(MvcResult result) {
		return (MockHttpSession) result.getRequest().getSession(false);
	}
}
