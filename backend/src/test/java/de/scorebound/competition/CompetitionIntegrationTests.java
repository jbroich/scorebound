package de.scorebound.competition;

import com.jayway.jsonpath.JsonPath;
import de.scorebound.identity.Account;
import de.scorebound.identity.AccountRepository;
import de.scorebound.identity.AccountService;
import de.scorebound.identity.Role;
import de.scorebound.teams.MemberRepository;
import de.scorebound.teams.MembershipRepository;
import de.scorebound.teams.TeamImageRepository;
import de.scorebound.teams.TeamRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class CompetitionIntegrationTests {

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

	@Autowired
	private ScoreboardRepository scoreboardRepository;

	@Autowired
	private ScoreboardTeamRepository scoreboardTeamRepository;

	@Autowired
	private CompetitionPeriodRepository periodRepository;

	@Autowired
	private PeriodParticipantRepository participantRepository;

	@Autowired
	private CompetitionService competitionService;

	private MockHttpSession adminSession;

	@BeforeEach
	void setUp() throws Exception {
		cleanDatabase();
		Account admin = accountService.createBootstrapAdmin("admin", "temporary-admin-password");
		accountService.changePassword(admin.getId(), "temporary-admin-password", ADMIN_PASSWORD);
		adminSession = sessionFrom(login("admin", ADMIN_PASSWORD).andReturn());
	}

	@AfterEach
	void tearDown() {
		cleanDatabase();
	}

	private void cleanDatabase() {
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

	@Test
	void snapshotsTeamsPreservesHistoryAndHandlesTiedWinners() throws Exception {
		String redTeamId = createTeam("Red Comets", "RED", "#E5484D");
		String blueTeamId = createTeam("Blue Orbit", "BLU", "#3366FF");
		String firstScoreboardId = createScoreboard("Apprentice League");
		String secondScoreboardId = createScoreboard("Side Quest");

		selectTeam(firstScoreboardId, redTeamId);
		selectTeam(firstScoreboardId, blueTeamId);
		selectTeam(secondScoreboardId, redTeamId);
		String periodId = schedulePeriod(firstScoreboardId, "Training 2026",
				Instant.now().minus(1, ChronoUnit.HOURS),
				Instant.now().plus(1, ChronoUnit.HOURS));

		mockMvc.perform(post("/api/v1/scoreboards/{scoreboardId}/periods/{periodId}/activate",
					firstScoreboardId, periodId).session(adminSession).with(csrf()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("Active"))
				.andExpect(jsonPath("$.participants.length()").value(2))
				.andExpect(jsonPath("$.participants[0].score").value(0))
				.andExpect(jsonPath("$.participants[0].rank").value(1))
				.andExpect(jsonPath("$.participants[1].rank").value(1));

		mockMvc.perform(delete("/api/v1/scoreboards/{scoreboardId}/teams/{teamId}",
					firstScoreboardId, blueTeamId).session(adminSession).with(csrf()))
				.andExpect(status().isNoContent());
		mockMvc.perform(get("/api/v1/scoreboards/{scoreboardId}/periods/{periodId}",
					firstScoreboardId, periodId).session(adminSession))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.participants.length()").value(2));

		mockMvc.perform(post("/api/v1/scoreboards/{scoreboardId}/periods/{periodId}/close",
					firstScoreboardId, periodId).session(adminSession).with(csrf()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("Closed"))
				.andExpect(jsonPath("$.participants[0].winner").value(true))
				.andExpect(jsonPath("$.participants[1].winner").value(true));

		mockMvc.perform(put("/api/v1/scoreboards/{scoreboardId}/periods/{periodId}/teams/{teamId}",
					firstScoreboardId, periodId, redTeamId).session(adminSession).with(csrf()))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("period_not_active"));

		mockMvc.perform(post("/api/v1/scoreboards/{scoreboardId}/periods/{periodId}/reopen",
					firstScoreboardId, periodId).session(adminSession).with(csrf()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("Active"))
				.andExpect(jsonPath("$.participants[0].winner").value(false));

		mockMvc.perform(post("/api/v1/scoreboards/{scoreboardId}/periods", firstScoreboardId)
					.session(adminSession).with(csrf()).contentType(APPLICATION_JSON)
					.content(periodJson("Overlap", Instant.now(),
							Instant.now().plus(2, ChronoUnit.HOURS))))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("period_overlap"));
	}

	@Test
	void permitsAuthenticatedReadsAndRestrictsAdministration() throws Exception {
		String scoreboardId = createScoreboard("Visible League");
		AccountService.CreatedAccount created = accountService.createWithTemporaryPassword(
				"viewer", Set.of(Role.MEMBER), "en", null);
		accountService.changePassword(created.account().getId(), created.temporaryPassword(),
				"permanent-viewer-password");
		MockHttpSession viewerSession = sessionFrom(login("viewer",
				"permanent-viewer-password").andReturn());

		mockMvc.perform(get("/api/v1/scoreboards/{scoreboardId}", scoreboardId)
					.session(viewerSession))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("Visible League"));

		mockMvc.perform(post("/api/v1/scoreboards").session(viewerSession).with(csrf())
					.contentType(APPLICATION_JSON).content("{\"name\":\"Forbidden\"}"))
				.andExpect(status().isForbidden());
	}

	@Test
	void requiresAuthenticationForLiveEventsAndStartsWithSnapshot() throws Exception {
		String scoreboardId = createScoreboard("Live League");

		mockMvc.perform(get("/api/v1/scoreboards/{scoreboardId}/events", scoreboardId))
				.andExpect(status().isUnauthorized());

		MvcResult stream = mockMvc.perform(get(
				"/api/v1/scoreboards/{scoreboardId}/events", scoreboardId)
						.session(adminSession))
				.andExpect(status().isOk())
				.andExpect(request().asyncStarted())
				.andReturn();
		assertThat(stream.getResponse().getContentAsString())
				.contains("event:snapshot", "\"scoreboardId\":\"" + scoreboardId + "\"");

		String teamId = createTeam("Live Team", "LIVE", "#123456");
		selectTeam(scoreboardId, teamId);
		assertThat(stream.getResponse().getContentAsString())
				.contains("event:participation-changed");
		stream.getRequest().getAsyncContext().complete();
	}

	@Test
	void automaticallyStartsAndClosesDuePeriodsButLeavesReopenedPeriodsActive() throws Exception {
		String teamId = createTeam("Lifecycle Team", "LIFE", "#123456");
		String scoreboardId = createScoreboard("Lifecycle League");
		selectTeam(scoreboardId, teamId);
		Instant startsAt = Instant.now().plus(1, ChronoUnit.HOURS);
		Instant endsAt = startsAt.plus(1, ChronoUnit.HOURS);
		String periodId = schedulePeriod(scoreboardId, "Automatic", startsAt, endsAt);

		competitionService.processLifecycle(startsAt.plusSeconds(1));
		CompetitionPeriod active = periodRepository.findById(java.util.UUID.fromString(periodId))
				.orElseThrow();
		assertThat(active.getStatus()).isEqualTo(PeriodStatus.ACTIVE);
		assertThat(participantRepository.findByPeriodIdOrderByPositionAsc(active.getId()))
				.singleElement().extracting(PeriodParticipant::getCurrentScore).isEqualTo(0L);

		competitionService.processLifecycle(endsAt.plusSeconds(1));
		CompetitionPeriod closed = periodRepository.findById(active.getId()).orElseThrow();
		assertThat(closed.getStatus()).isEqualTo(PeriodStatus.CLOSED);
		assertThat(participantRepository.findByPeriodIdOrderByPositionAsc(active.getId()))
				.singleElement().extracting(PeriodParticipant::isWinner).isEqualTo(true);

		mockMvc.perform(post("/api/v1/scoreboards/{scoreboardId}/periods/{periodId}/reopen",
					scoreboardId, periodId).session(adminSession).with(csrf()))
				.andExpect(status().isOk());
		competitionService.processLifecycle(endsAt.plus(2, ChronoUnit.HOURS));
		assertThat(periodRepository.findById(active.getId()).orElseThrow().getStatus())
				.isEqualTo(PeriodStatus.ACTIVE);
	}

	private String createTeam(String name, String shortName, String color) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/teams").session(adminSession).with(csrf())
					.contentType(APPLICATION_JSON)
					.content("""
							{"name":"%s","shortName":"%s","color":"%s"}
							""".formatted(name, shortName, color)))
				.andExpect(status().isCreated()).andReturn();
		return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
	}

	private String createScoreboard(String name) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/scoreboards")
					.session(adminSession).with(csrf()).contentType(APPLICATION_JSON)
					.content("{\"name\":\"" + name + "\"}"))
				.andExpect(status().isCreated()).andReturn();
		return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
	}

	private void selectTeam(String scoreboardId, String teamId) throws Exception {
		mockMvc.perform(put("/api/v1/scoreboards/{scoreboardId}/teams/{teamId}",
				scoreboardId, teamId).session(adminSession).with(csrf()))
				.andExpect(status().isNoContent());
	}

	private String schedulePeriod(String scoreboardId, String name, Instant startsAt,
			Instant endsAt) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/scoreboards/{scoreboardId}/periods",
					scoreboardId).session(adminSession).with(csrf()).contentType(APPLICATION_JSON)
					.content(periodJson(name, startsAt, endsAt)))
				.andExpect(status().isCreated()).andReturn();
		return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
	}

	private static String periodJson(String name, Instant startsAt, Instant endsAt) {
		return """
				{
				  "name":"%s",
				  "startsAt":"%s",
				  "endsAt":"%s"
				}
				""".formatted(name, startsAt, endsAt);
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
}
