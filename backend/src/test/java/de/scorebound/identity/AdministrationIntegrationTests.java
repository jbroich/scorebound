package de.scorebound.identity;

import com.jayway.jsonpath.JsonPath;
import de.scorebound.competition.CompetitionPeriodRepository;
import de.scorebound.competition.PeriodParticipantRepository;
import de.scorebound.competition.ScoreboardRepository;
import de.scorebound.competition.ScoreboardTeamRepository;
import de.scorebound.scoring.ScoreCancellationRepository;
import de.scorebound.scoring.ScoreTransactionRepository;
import de.scorebound.scoring.ScorerAssignmentRepository;
import de.scorebound.teams.MemberRepository;
import de.scorebound.teams.MembershipRepository;
import de.scorebound.teams.TeamImageRepository;
import de.scorebound.teams.TeamRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Set;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AdministrationIntegrationTests {

	@Autowired private MockMvc mockMvc;
	@Autowired private AccountRepository accountRepository;
	@Autowired private AccountService accountService;
	@Autowired private TeamRepository teamRepository;
	@Autowired private MemberRepository memberRepository;
	@Autowired private MembershipRepository membershipRepository;
	@Autowired private TeamImageRepository teamImageRepository;
	@Autowired private ScoreboardRepository scoreboardRepository;
	@Autowired private ScoreboardTeamRepository scoreboardTeamRepository;
	@Autowired private CompetitionPeriodRepository periodRepository;
	@Autowired private PeriodParticipantRepository participantRepository;
	@Autowired private ScorerAssignmentRepository assignmentRepository;
	@Autowired private ScoreTransactionRepository transactionRepository;
	@Autowired private ScoreCancellationRepository cancellationRepository;

	private MockHttpSession adminSession;
	private Account admin;

	@BeforeEach
	void setUp() throws Exception {
		cleanDatabase();
		admin = accountService.createBootstrapAdmin("admin", "temporary-admin-password");
		accountService.changePassword(admin.getId(), "temporary-admin-password",
				"permanent-admin-password");
		adminSession = sessionFrom(login("admin", "permanent-admin-password").andReturn());
	}

	@AfterEach
	void tearDown() {
		cleanDatabase();
	}

	@Test
	void listsAndUpdatesAccountsWithoutAllowingSelfLockout() throws Exception {
		MvcResult created = mockMvc.perform(post("/api/v1/accounts").session(adminSession).with(csrf())
					.contentType(APPLICATION_JSON)
					.content("{\"username\":\"score-user\",\"roles\":[\"Member\"],\"preferredLocale\":\"en\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.temporaryPassword").isNotEmpty())
				.andReturn();
		String accountId = JsonPath.read(created.getResponse().getContentAsString(), "$.id");

		mockMvc.perform(patch("/api/v1/accounts/{accountId}", accountId)
					.session(adminSession).with(csrf()).contentType(APPLICATION_JSON)
					.content("{\"roles\":[\"Scorer\",\"Member\"],\"preferredLocale\":\"de\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.roles.length()").value(2))
				.andExpect(jsonPath("$.preferredLocale").value("de"))
				.andExpect(jsonPath("$.scorerAssignments.length()").value(0));

		mockMvc.perform(get("/api/v1/accounts").session(adminSession))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[?(@.username == 'score-user')]").exists());

		mockMvc.perform(patch("/api/v1/accounts/{accountId}", admin.getId())
					.session(adminSession).with(csrf()).contentType(APPLICATION_JSON)
					.content("{\"enabled\":false}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("cannot_remove_own_admin_access"));
	}

	@Test
	void restrictsAdministrativeListsAndCanIncludeInactiveResources() throws Exception {
		String teamId = createTeam();
		String scoreboardId = createScoreboard();
		mockMvc.perform(patch("/api/v1/teams/{teamId}", teamId).session(adminSession).with(csrf())
					.contentType(APPLICATION_JSON).content("{\"active\":false}"))
				.andExpect(status().isOk());
		mockMvc.perform(patch("/api/v1/scoreboards/{scoreboardId}", scoreboardId)
					.session(adminSession).with(csrf()).contentType(APPLICATION_JSON)
					.content("{\"active\":false}"))
				.andExpect(status().isOk());

		mockMvc.perform(get("/api/v1/teams").session(adminSession))
				.andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
		mockMvc.perform(get("/api/v1/teams?includeInactive=true").session(adminSession))
				.andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(teamId));
		mockMvc.perform(get("/api/v1/scoreboards?includeInactive=true").session(adminSession))
				.andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(scoreboardId));

		AccountService.CreatedAccount viewer = accountService.createWithTemporaryPassword(
				"viewer", Set.of(Role.MEMBER), "en", null);
		accountService.changePassword(viewer.account().getId(), viewer.temporaryPassword(),
				"permanent-viewer-password");
		MockHttpSession viewerSession = sessionFrom(login("viewer", "permanent-viewer-password")
				.andReturn());
		mockMvc.perform(get("/api/v1/accounts").session(viewerSession)).andExpect(status().isForbidden());
		mockMvc.perform(get("/api/v1/teams?includeInactive=true").session(viewerSession))
				.andExpect(status().isForbidden());
		mockMvc.perform(get("/api/v1/scoreboards?includeInactive=true").session(viewerSession))
				.andExpect(status().isForbidden());
	}

	private String createTeam() throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/teams").session(adminSession).with(csrf())
					.contentType(APPLICATION_JSON)
					.content("{\"name\":\"Archived Team\",\"shortName\":\"OLD\",\"color\":\"#123456\"}"))
				.andExpect(status().isCreated()).andReturn();
		return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
	}

	private String createScoreboard() throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/scoreboards")
					.session(adminSession).with(csrf()).contentType(APPLICATION_JSON)
					.content("{\"name\":\"Archived League\"}"))
				.andExpect(status().isCreated()).andReturn();
		return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
	}

	private org.springframework.test.web.servlet.ResultActions login(String username,
			String password) throws Exception {
		return mockMvc.perform(post("/api/v1/sessions").contentType(APPLICATION_JSON)
				.content("{\"username\":\"" + username + "\",\"password\":\"" + password
						+ "\",\"mode\":\"Normal\"}"));
	}

	private static MockHttpSession sessionFrom(MvcResult result) {
		return (MockHttpSession) result.getRequest().getSession(false);
	}

	private void cleanDatabase() {
		cancellationRepository.deleteAll();
		transactionRepository.deleteAll();
		assignmentRepository.deleteAll();
		participantRepository.deleteAll();
		periodRepository.deleteAll();
		scoreboardTeamRepository.deleteAll();
		scoreboardRepository.deleteAll();
		teamImageRepository.deleteAll();
		accountRepository.deleteAll();
		membershipRepository.deleteAll();
		memberRepository.deleteAll();
		teamRepository.deleteAll();
	}
}
