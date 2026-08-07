package de.scorebound.scoring;

import com.jayway.jsonpath.JsonPath;
import de.scorebound.competition.CompetitionPeriodRepository;
import de.scorebound.competition.PeriodParticipantRepository;
import de.scorebound.competition.ScoreboardRepository;
import de.scorebound.competition.ScoreboardTeamRepository;
import de.scorebound.identity.Account;
import de.scorebound.identity.AccountRepository;
import de.scorebound.identity.AccountService;
import de.scorebound.identity.Role;
import de.scorebound.teams.MemberRepository;
import de.scorebound.teams.MembershipRepository;
import de.scorebound.teams.TeamImageRepository;
import de.scorebound.teams.TeamRepository;
import de.scorebound.web.ApiException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

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
class ScoringIntegrationTests {

	private static final String ADMIN_PASSWORD = "permanent-admin-password";

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
	@Autowired private ScoringService scoringService;

	private MockHttpSession adminSession;
	private UUID adminAccountId;

	@BeforeEach
	void setUp() throws Exception {
		cleanDatabase();
		Account admin = accountService.createBootstrapAdmin("admin", "temporary-admin-password");
		adminAccountId = admin.getId();
		accountService.changePassword(admin.getId(), "temporary-admin-password", ADMIN_PASSWORD);
		adminSession = sessionFrom(login("admin", ADMIN_PASSWORD).andReturn());
	}

	@AfterEach
	void tearDown() {
		cleanDatabase();
	}

	@Test
	void recordsTeamAndMemberTransactionsWithIdempotencyAndHistoricalOwnership() throws Exception {
		String redTeamId = createTeam("Red Comets", "RED", "#E5484D");
		String blueTeamId = createTeam("Blue Orbit", "BLU", "#3366FF");
		String memberId = createMember("Ada", redTeamId);
		ActiveCompetition competition = createActiveCompetition(redTeamId, blueTeamId);

		MvcResult credit = score(competition.scoreboardId(), adminSession, "credit-1",
				"{\"teamId\":\"" + redTeamId
						+ "\",\"kind\":\"Credit\",\"amount\":100,\"reason\":\"Great presentation\"}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.resultingTeamScore").value(100))
				.andReturn();
		String creditId = JsonPath.read(credit.getResponse().getContentAsString(), "$.id");

		MvcResult retry = score(competition.scoreboardId(), adminSession, "credit-1",
				"{\"teamId\":\"" + redTeamId
						+ "\",\"kind\":\"Credit\",\"amount\":100,\"reason\":\"Great presentation\"}")
				.andExpect(status().isOk()).andReturn();
		assertThat(JsonPath.<String>read(retry.getResponse().getContentAsString(), "$.id"))
				.isEqualTo(creditId);
		assertThat(transactionRepository.count()).isEqualTo(1);

		score(competition.scoreboardId(), adminSession, "credit-1",
				"{\"teamId\":\"" + redTeamId
						+ "\",\"kind\":\"Credit\",\"amount\":50,\"reason\":\"Great presentation\"}")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("idempotency_key_reused"));

		score(competition.scoreboardId(), adminSession, null,
				"{\"teamId\":\"" + redTeamId
						+ "\",\"kind\":\"Debit\",\"amount\":25,\"reason\":\"Rule reminder\"}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.resultingTeamScore").value(75));
		score(competition.scoreboardId(), adminSession, null,
				"{\"teamId\":\"" + redTeamId
						+ "\",\"kind\":\"Debit\",\"amount\":100,\"reason\":\"Too much\"}")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("score_would_be_negative"));

		MvcResult memberCredit = score(competition.scoreboardId(), adminSession, null,
				"{\"memberId\":\"" + memberId
						+ "\",\"kind\":\"Credit\",\"amount\":50,\"reason\":\"Helpful review\"}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.teamId").value(redTeamId))
				.andExpect(jsonPath("$.resultingTeamScore").value(125))
				.andReturn();
		String memberTransactionId = JsonPath.read(memberCredit.getResponse().getContentAsString(), "$.id");

		mockMvc.perform(post("/api/v1/members/{memberId}/team-changes", memberId)
					.session(adminSession).with(csrf()).contentType(APPLICATION_JSON)
					.content("{\"teamId\":\"" + blueTeamId + "\"}"))
				.andExpect(status().isOk());

		mockMvc.perform(get("/api/v1/scoreboards/{scoreboardId}/transactions",
					competition.scoreboardId()).session(adminSession))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[?(@.id == '%s')].teamId"
						.formatted(memberTransactionId)).value(redTeamId));
		mockMvc.perform(get("/api/v1/scoreboards/{scoreboardId}/standings",
					competition.scoreboardId()).session(adminSession))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.standings[0].teamId").value(redTeamId))
				.andExpect(jsonPath("$.standings[0].score").value(125));
	}

	@Test
	void exposesCancellationTrailAndEnforcesScorerAssignmentsAndOwnership() throws Exception {
		String teamId = createTeam("Scoring Team", "PTS", "#123456");
		ActiveCompetition competition = createActiveCompetition(teamId);
		Scorer scorer = createScorer("first-scorer");
		Scorer other = createScorer("other-scorer");
		assignScorer(scorer.accountId(), competition.scoreboardId());
		assignScorer(other.accountId(), competition.scoreboardId());

		MvcResult result = score(competition.scoreboardId(), scorer.session(), null,
				"{\"teamId\":\"" + teamId
						+ "\",\"kind\":\"Credit\",\"amount\":50,\"reason\":\"Nice work\"}")
				.andExpect(status().isOk()).andReturn();
		String transactionId = JsonPath.read(result.getResponse().getContentAsString(), "$.id");

		mockMvc.perform(post("/api/v1/scoreboards/{scoreboardId}/transactions/{transactionId}/cancellation",
					competition.scoreboardId(), transactionId).session(other.session()).with(csrf())
					.contentType(APPLICATION_JSON).content("{\"reason\":\"Not mine\"}"))
				.andExpect(status().isForbidden());

		mockMvc.perform(post("/api/v1/scoreboards/{scoreboardId}/transactions/{transactionId}/cancellation",
					competition.scoreboardId(), transactionId).session(scorer.session()).with(csrf())
					.contentType(APPLICATION_JSON).content("{\"reason\":\"Entered twice\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.resultingTeamScore").value(0))
				.andExpect(jsonPath("$.cancellation.reason").value("Entered twice"));

		mockMvc.perform(get("/api/v1/scoreboards/{scoreboardId}/transactions",
					competition.scoreboardId()).session(scorer.session()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].cancellation.reason").value("Entered twice"));

		String otherScoreboard = createScoreboard("Unassigned League");
		score(otherScoreboard, scorer.session(), null,
				"{\"teamId\":\"" + teamId
						+ "\",\"kind\":\"Credit\",\"amount\":10,\"reason\":\"No access\"}")
				.andExpect(status().isForbidden());
	}

	@Test
	void rejectsChangesWhileClosedAndAllowsThemAfterReopening() throws Exception {
		String teamId = createTeam("Period Team", "PER", "#ABCDEF");
		ActiveCompetition competition = createActiveCompetition(teamId);
		mockMvc.perform(post("/api/v1/scoreboards/{scoreboardId}/periods/{periodId}/close",
					competition.scoreboardId(), competition.periodId())
					.session(adminSession).with(csrf())).andExpect(status().isOk());

		score(competition.scoreboardId(), adminSession, null,
				"{\"teamId\":\"" + teamId
						+ "\",\"kind\":\"Credit\",\"amount\":10,\"reason\":\"Closed\"}")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("period_not_active"));

		mockMvc.perform(post("/api/v1/scoreboards/{scoreboardId}/periods/{periodId}/reopen",
					competition.scoreboardId(), competition.periodId())
					.session(adminSession).with(csrf())).andExpect(status().isOk());
		score(competition.scoreboardId(), adminSession, null,
				"{\"teamId\":\"" + teamId
						+ "\",\"kind\":\"Credit\",\"amount\":10,\"reason\":\"Reopened\"}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.resultingTeamScore").value(10));
	}

	@Test
	void serializesConcurrentDebitsSoTheScoreNeverBecomesNegative() throws Exception {
		String teamId = createTeam("Concurrent Team", "LOCK", "#445566");
		ActiveCompetition competition = createActiveCompetition(teamId);
		UUID scoreboardId = UUID.fromString(competition.scoreboardId());
		UUID targetTeamId = UUID.fromString(teamId);
		scoringService.record(scoreboardId, targetTeamId, null, ScoreKind.CREDIT, 100,
				"Starting credit", null, adminAccountId, Set.of(Role.ADMIN));

		var executor = Executors.newFixedThreadPool(2);
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		java.util.concurrent.Callable<String> debit = () -> {
			ready.countDown();
			start.await();
			try {
				scoringService.record(scoreboardId, targetTeamId, null, ScoreKind.DEBIT, 75,
						"Concurrent debit", null, adminAccountId, Set.of(Role.ADMIN));
				return "accepted";
			} catch (ApiException exception) {
				return exception.getCode();
			}
		};
		try {
			var first = executor.submit(debit);
			var second = executor.submit(debit);
			assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
			start.countDown();
			assertThat(java.util.List.of(first.get(10, TimeUnit.SECONDS),
					second.get(10, TimeUnit.SECONDS)))
					.containsExactlyInAnyOrder("accepted", "score_would_be_negative");
		} finally {
			executor.shutdownNow();
		}

		assertThat(participantRepository.findByPeriodIdAndTeamId(
				UUID.fromString(competition.periodId()), targetTeamId).orElseThrow().getCurrentScore())
				.isEqualTo(25L);
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

	private String createTeam(String name, String shortName, String color) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/teams").session(adminSession).with(csrf())
					.contentType(APPLICATION_JSON).content("{\"name\":\"" + name
							+ "\",\"shortName\":\"" + shortName + "\",\"color\":\"" + color + "\"}"))
				.andExpect(status().isCreated()).andReturn();
		return JsonPath.read(result.getResponse().getContentAsString(), "$.id");
	}

	private String createMember(String name, String teamId) throws Exception {
		MvcResult result = mockMvc.perform(post("/api/v1/members").session(adminSession).with(csrf())
					.contentType(APPLICATION_JSON).content("{\"displayName\":\"" + name
							+ "\",\"teamId\":\"" + teamId + "\"}"))
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

	private ActiveCompetition createActiveCompetition(String... teamIds) throws Exception {
		String scoreboardId = createScoreboard("Active League " + java.util.UUID.randomUUID());
		for (String teamId : teamIds) {
			mockMvc.perform(put("/api/v1/scoreboards/{scoreboardId}/teams/{teamId}", scoreboardId,
					teamId).session(adminSession).with(csrf())).andExpect(status().isNoContent());
		}
		Instant startsAt = Instant.now().minus(1, ChronoUnit.HOURS);
		Instant endsAt = Instant.now().plus(1, ChronoUnit.HOURS);
		MvcResult scheduled = mockMvc.perform(post("/api/v1/scoreboards/{scoreboardId}/periods",
					scoreboardId).session(adminSession).with(csrf()).contentType(APPLICATION_JSON)
					.content("{\"name\":\"Current\",\"startsAt\":\"" + startsAt
							+ "\",\"endsAt\":\"" + endsAt + "\"}"))
				.andExpect(status().isCreated()).andReturn();
		String periodId = JsonPath.read(scheduled.getResponse().getContentAsString(), "$.id");
		mockMvc.perform(post("/api/v1/scoreboards/{scoreboardId}/periods/{periodId}/activate",
				scoreboardId, periodId).session(adminSession).with(csrf())).andExpect(status().isOk());
		return new ActiveCompetition(scoreboardId, periodId);
	}

	private Scorer createScorer(String username) throws Exception {
		AccountService.CreatedAccount created = accountService.createWithTemporaryPassword(username,
				Set.of(Role.SCORER), "en", null);
		String password = "permanent-scorer-password";
		accountService.changePassword(created.account().getId(), created.temporaryPassword(), password);
		return new Scorer(created.account().getId().toString(),
				sessionFrom(login(username, password).andReturn()));
	}

	private void assignScorer(String accountId, String scoreboardId) throws Exception {
		mockMvc.perform(put("/api/v1/accounts/{accountId}/scorer-assignments/{scoreboardId}",
				accountId, scoreboardId).session(adminSession).with(csrf()))
				.andExpect(status().isNoContent());
	}

	private org.springframework.test.web.servlet.ResultActions score(String scoreboardId,
			MockHttpSession session, String idempotencyKey, String body) throws Exception {
		var request = post("/api/v1/scoreboards/{scoreboardId}/transactions", scoreboardId)
				.session(session).with(csrf()).contentType(APPLICATION_JSON).content(body);
		if (idempotencyKey != null) {
			request.header("Idempotency-Key", idempotencyKey);
		}
		return mockMvc.perform(request);
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

	private record ActiveCompetition(String scoreboardId, String periodId) {
	}

	private record Scorer(String accountId, MockHttpSession session) {
	}
}
