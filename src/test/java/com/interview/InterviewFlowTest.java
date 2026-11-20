package com.interview;

import com.interview.repository.AnswerRepository;
import com.interview.repository.QuestionRepository;
import com.interview.repository.SessionRepository;
import com.interview.service.GroqService;
import com.interview.support.GroqStub;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:flow;MODE=MySQL",
        "claude.api-url=http://127.0.0.1:1/not-used"})
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@ActiveProfiles("test")
class InterviewFlowTest {

    private static final String SOURCE = "가상 캐시 프로젝트";
    private static final String FIRST = "캐시를 선택했습니다.";
    private static final String FOLLOW = "캐시를 선택한 기준은?";
    private static final String INITIAL = """
            {"technicalAccuracy":"NOT_ASSESSABLE","reasoning":"INSUFFICIENT","clarity":"CLEAR",
             "strengths":[],"improvements":["선택 근거"],"uncertainties":["실제 성과"],
             "summary":"근거를 더 설명해 주세요.","followUpQuestion":"캐시를 선택한 기준은?"}
            """;
    private static final String FINAL = """
            {"technicalAccuracy":"CORRECT","reasoning":"SUFFICIENT","clarity":"CLEAR",
             "strengths":["적합성 설명"],"improvements":[],"uncertainties":[],
             "summary":"적합한 이유를 설명했습니다.","followUpQuestion":null}
            """;

    @TestConfiguration
    static class Config {
        @Bean GroqStub groqStub() throws IOException { return new GroqStub(); }
        @Bean @Primary GroqService loopbackClient(GroqStub stub) {
            return new GroqService("test-only", stub.endpoint(), Duration.ofSeconds(1), Duration.ofSeconds(3));
        }
    }

    @Autowired MockMvc mvc;
    @Autowired JsonMapper json;
    @Autowired GroqStub stub;
    @Autowired JdbcTemplate db;
    @Autowired HikariDataSource dataSource;
    @Autowired SessionRepository sessions;
    @Autowired QuestionRepository questions;
    @Autowired AnswerRepository answers;
    String owner;
    long resume;

    @BeforeEach
    void setup() throws Exception {
        owner = signup();
        resume = created(send(owner, "/api/resumes", Map.of("text", SOURCE))).path("id").asLong();
    }

    @Test
    void storesFiveGroundedQuestionsThenPreservesBothEvaluations() throws Exception {
        Counts before = counts();
        JsonNode session = start();
        long sid = session.path("id").asLong();
        assertThat(lastContext().path("resumeText").asString()).isEqualTo(SOURCE);
        var storedSession = sessions.findById(sid).orElseThrow();
        assertThat(storedSession.getResume().getId()).isEqualTo(resume);
        assertThat(storedSession.getModel()).isEqualTo("openai/gpt-oss-120b");
        assertThat(storedSession.getCriteriaVersion()).isNotBlank();
        JsonNode roots = session.path("questions");
        assertThat(roots.size()).isEqualTo(5);
        for (int i = 0; i < 5; i++) {
            JsonNode root = roots.get(i);
            assertThat(root.path("id").asLong()).isPositive();
            assertThat(root.path("position").asInt()).isEqualTo(i + 1);
            assertThat(root.path("content").asString()).isEqualTo("선택 근거 " + (i + 1));
            assertThat(root.path("sourceQuote").asString()).isEqualTo(SOURCE);
            assertThat(root.path("evaluationCriteria").get(0).asString()).isEqualTo("근거");
            assertThat(root.path("intent").asString()).isEqualTo("이유 확인");
        }

        long qid = roots.get(3).path("id").asLong();
        stub.respondContent(INITIAL);
        JsonNode first = created(answer(owner, sid, qid, FIRST));
        JsonNode context = lastContext();
        assertThat(context.path("originalQuestion").path("content").asString()).isEqualTo("선택 근거 4");
        assertThat(context.path("originalQuestion").path("sourceQuote").asString()).isEqualTo(SOURCE);
        assertThat(context.path("originalAnswer").asString()).isEqualTo(FIRST);
        for (String field : List.of("initialEvaluation", "followUpQuestion", "followUpAnswer")) {
            assertThat(context.path(field).isNull()).isTrue();
        }
        assertThat(first.path("evaluation")).isEqualTo(json.readTree(INITIAL));
        JsonNode follow = first.path("followUpQuestion");
        long child = follow.path("id").asLong();
        assertThat(child).isPositive();
        assertThat(follow.path("content").asString()).isEqualTo(FOLLOW);
        assertThat(storedEvaluation(first)).isEqualTo(json.readTree(INITIAL));
        var storedAnswer = answers.findById(first.path("id").asLong()).orElseThrow();
        assertThat(storedAnswer.getModel()).isEqualTo(storedSession.getModel());
        assertThat(storedAnswer.getCriteriaVersion()).isEqualTo(storedSession.getCriteriaVersion());
        assertThat(storedAnswer.getScore()).isNull();

        Counts pending = counts();
        answer(owner, sid, child, "성능 기준").andExpect(status().isBadGateway()); // 모델이 두 번째 꼬리를 요청
        assertThat(counts()).isEqualTo(pending);
        stub.respondContent(FINAL);
        JsonNode combined = created(answer(owner, sid, child, "성능 기준"));
        context = lastContext();
        assertThat(context.path("originalAnswer").asString()).isEqualTo(FIRST);
        assertThat(context.path("initialEvaluation")).isEqualTo(json.readTree(INITIAL));
        assertThat(context.path("followUpQuestion").asString()).isEqualTo(FOLLOW);
        assertThat(context.path("followUpAnswer").asString()).isEqualTo("성능 기준");
        assertThat(combined.path("evaluation")).isEqualTo(json.readTree(FINAL));
        assertThat(combined.path("followUpQuestion").isNull()).isTrue();
        assertThat(storedEvaluation(first)).isEqualTo(json.readTree(INITIAL));
        assertThat(storedEvaluation(combined)).isEqualTo(json.readTree(FINAL));
        assertThat(counts()).isEqualTo(before.plus(1, 6, 2));

        int calls = stub.requests().size();
        answer(owner, sid, qid, FIRST).andExpect(status().isConflict());
        answer(owner, sid, child, "재제출").andExpect(status().isConflict());
        assertThat(stub.requests()).hasSize(calls);
    }

    @Test
    void reloadsOwnedHistoryFromLoginThroughCompletion() throws Exception {
        var credentials = Map.of("email", UUID.randomUUID() + "@example.test", "password", "test-password");
        created(send("", "/api/auth/signup", credentials));
        String token = "Bearer " + ok(send("", "/api/auth/login", credentials)).path("token").asString();
        String snapshot = "  " + SOURCE + "\n아키텍처를 개선했습니다.\n";
        long resumeId = created(send(token, "/api/resumes", Map.of("text", snapshot))).path("id").asLong();
        serveFiveRoots();
        JsonNode session = created(send(token, "/api/sessions", Map.of("resumeId", resumeId)));
        long sid = session.path("id").asLong();
        long rootId = firstId(session);
        long secondId = session.path("questions").get(1).path("id").asLong();
        String original = "  " + FIRST + "\n";
        stub.respondContent(INITIAL);
        long followUpId = created(answer(token, sid, rootId, original))
                .path("followUpQuestion").path("id").asLong();

        JsonNode pending = ok(detail(token, sid)).path("questions");
        assertThat(pending.get(1).path("id").asLong()).isEqualTo(followUpId);
        assertThat(pending.get(1).get("answers").isArray()).isTrue();
        assertThat(pending.get(1).get("answers").size()).isZero();

        String additional = "  성능 기준과 요구사항\n";
        stub.respondContent(FINAL);
        created(answer(token, sid, followUpId, additional));
        // Leave a different follow-up pending when the interview ends.
        stub.respondContent(INITIAL);
        long pendingId = created(answer(token, sid, secondId, FIRST))
                .path("followUpQuestion").path("id").asLong();
        int calls = stub.requests().size();

        JsonNode history = ok(detail(token, sid));
        assertThat(history.path("status").asString()).isEqualTo("IN_PROGRESS");
        assertThat(history.path("resume").path("id").asLong()).isEqualTo(resumeId);
        assertThat(history.path("resume").path("text").asString()).isEqualTo(snapshot);
        assertThat(history.path("resume").path("createdAt").asString()).isNotBlank();
        assertThat(history.get("categoryName").isNull()).isTrue();
        assertThat(history.path("model").asString()).isEqualTo("openai/gpt-oss-120b");
        assertThat(history.path("criteriaVersion").asString()).isEqualTo("resume-interview-v1");
        JsonNode rows = history.path("questions");
        assertThat(rows.size()).isEqualTo(7);
        List<Long> orderedIds = List.of(rootId, followUpId, secondId, pendingId,
                session.path("questions").get(2).path("id").asLong(),
                session.path("questions").get(3).path("id").asLong(),
                session.path("questions").get(4).path("id").asLong());
        for (int i = 0; i < rows.size(); i++) {
            assertThat(rows.get(i).path("id").asLong()).isEqualTo(orderedIds.get(i));
            assertThat(rows.get(i).path("sourceQuote").asString()).isEqualTo(SOURCE);
            assertThat(rows.get(i).path("intent").asString()).isEqualTo("이유 확인");
            assertThat(rows.get(i).path("evaluationCriteria").get(0).asString()).isEqualTo("근거");
        }
        assertThat(rows.get(0).path("position").asInt()).isEqualTo(1);
        assertThat(rows.get(0).get("parentQuestionId").isNull()).isTrue();
        assertThat(rows.get(1).path("position").asInt()).isEqualTo(1);
        assertThat(rows.get(1).path("parentQuestionId").asLong()).isEqualTo(rootId);
        assertThat(rows.get(1).path("content").asString()).isEqualTo(FOLLOW);
        assertThat(rows.get(3).path("parentQuestionId").asLong()).isEqualTo(secondId);
        assertThat(rows.get(3).get("answers").isArray()).isTrue();
        assertThat(rows.get(3).get("answers").size()).isZero();
        assertThat(rows.get(4).get("answers").isArray()).isTrue();
        assertThat(rows.get(4).get("answers").size()).isZero();
        assertStoredAnswer(rows.get(0), original, INITIAL, "INITIAL");
        assertThat(rows.get(0).path("answers").get(0).path("followUpQuestion").path("id").asLong())
                .isEqualTo(followUpId);
        assertStoredAnswer(rows.get(1), additional, FINAL, "COMPREHENSIVE");

        done(token, sid).andExpect(status().isOk());
        JsonNode completed = ok(detail(token, sid));
        assertThat(completed.path("status").asString()).isEqualTo("DONE");
        assertThat(completed.path("resume")).isEqualTo(history.path("resume"));
        assertThat(completed.path("questions")).isEqualTo(rows);
        answer(token, sid, pendingId, "완료 뒤 제출").andExpect(status().isBadRequest());
        assertThat(stub.requests()).hasSize(calls);
    }

    @Test
    void doesNotDiscloseAnotherUsersSessionOrResumeSnapshot() throws Exception {
        long sid = start().path("id").asLong();
        String other = signup();
        mvc.perform(get("/api/sessions/{id}", sid)).andExpect(status().isForbidden());
        detail(other, sid).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("세션을 찾을 수 없습니다."))
                .andExpect(jsonPath("$.resume").doesNotHaveJsonPath());
        detail(other, Long.MAX_VALUE).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("세션을 찾을 수 없습니다."));
        mvc.perform(get("/api/sessions").header("Authorization", other))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "not-a-session", "1.5", "9223372036854775808"})
    void returnsSafeBadRequestForInvalidDetailId(String id) throws Exception {
        detail(owner, id).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("요청 입력값이 올바르지 않습니다."));
    }

    @Test
    void listsMixedSessionsWithoutResumeTextAndKeepsLegacyDuplicatesAndAverages() throws Exception {
        String category = "기존 주제 " + UUID.randomUUID();
        db.update("insert into categories (name) values (?)", category);
        long categoryId = db.queryForObject("select id from categories where name = ?", Long.class, category);
        db.update("insert into questions (category_id, content) values (?, ?)", categoryId, "기존 고정 질문");
        long fixedId = db.queryForObject("select id from questions where category_id = ?", Long.class, categoryId);
        long legacy = legacySession(resume, categoryId);
        legacyAnswer(legacy, fixedId, "기존 답변 A", 7);
        legacyAnswer(legacy, fixedId, "기존 답변 B", 9);

        String other = signup();
        long otherResume = created(send(other, "/api/resumes", Map.of("text", SOURCE))).path("id").asLong();
        legacyAnswer(legacySession(otherResume, categoryId), fixedId, "타인 답변", 100);

        JsonNode current = start();
        long currentId = current.path("id").asLong();
        stub.respondContent(FINAL);
        created(answer(owner, currentId, firstId(current), FIRST));
        mvc.perform(get("/api/stats").header("Authorization", owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSessions").value(2))
                .andExpect(jsonPath("$.completedSessions").value(1))
                .andExpect(jsonPath("$.overallAverageScore").value(8.0))
                .andExpect(jsonPath("$.categoryStats.length()").value(1))
                .andExpect(jsonPath("$.categoryStats[0].categoryName").value(category))
                .andExpect(jsonPath("$.categoryStats[0].averageScore").value(8.0))
                .andExpect(jsonPath("$.categoryStats[0].answerCount").value(2))
                .andExpect(jsonPath("$.averageScoreScope").value("SCORED_LEGACY_ANSWERS"));

        mvc.perform(get("/api/sessions").header("Authorization", owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(currentId))
                .andExpect(jsonPath("$[0].resumeId").value(resume))
                .andExpect(jsonPath("$[0].resume.text").doesNotHaveJsonPath())
                .andExpect(jsonPath("$[1].id").value(legacy))
                .andExpect(jsonPath("$[1].categoryName").value(category));
        detail(owner, legacy).andExpect(status().isOk())
                .andExpect(jsonPath("$.resumeId").value(nullValue()))
                .andExpect(jsonPath("$.questions.length()").value(1))
                .andExpect(jsonPath("$.questions[0].id").value(fixedId))
                .andExpect(jsonPath("$.questions[0].content").value("기존 고정 질문"))
                .andExpect(jsonPath("$.questions[0].categoryName").value(category))
                .andExpect(jsonPath("$.questions[0].answers.length()").value(2))
                .andExpect(jsonPath("$.questions[0].answers[0].userAnswer").value("기존 답변 A"))
                .andExpect(jsonPath("$.questions[0].answers[0].score").value(7))
                .andExpect(jsonPath("$.questions[0].answers[1].userAnswer").value("기존 답변 B"))
                .andExpect(jsonPath("$.questions[0].answers[1].score").value(9))
                .andExpect(jsonPath("$.questions[0].answers[1].feedback").value("기존 평가"))
                .andExpect(jsonPath("$.questions[0].answers[1].evaluation").value(nullValue()));
        detail(owner, currentId).andExpect(status().isOk())
                .andExpect(jsonPath("$.questions[0].categoryName").value(nullValue()))
                .andExpect(jsonPath("$.questions[0].answers[0].feedback").value(nullValue()))
                .andExpect(jsonPath("$.questions[0].answers[0].score").value(nullValue()));
    }

    @Test
    void rejectsForeignResourcesUnrelatedAndFixedQuestionsBeforeCallingProvider() throws Exception {
        String stranger = signup();
        int calls = stub.requests().size();
        send(stranger, "/api/sessions", Map.of("resumeId", resume)).andExpect(status().isNotFound());
        assertThat(stub.requests()).hasSize(calls);
        JsonNode own = start();
        long sid = own.path("id").asLong();
        long qid = firstId(own);
        long unrelated = firstId(start());
        db.update("insert into questions(content) values (?)", "기존 고정 문제");
        long fixed = db.queryForObject("select max(id) from questions where session_id is null", Long.class);
        stub.respondContent(INITIAL);
        long child = created(answer(owner, sid, qid, FIRST)).path("followUpQuestion").path("id").asLong();
        calls = stub.requests().size();
        answer(owner, sid, unrelated, FIRST).andExpect(status().isNotFound());
        answer(owner, sid, fixed, FIRST).andExpect(status().isNotFound());
        answer(stranger, sid, qid, FIRST).andExpect(status().isNotFound());
        answer(stranger, sid, child, FIRST).andExpect(status().isNotFound());
        done(stranger, sid).andExpect(status().isNotFound());
        assertThat(stub.requests()).hasSize(calls);
    }

    @Test
    void validatesRequestsBeforeCallingProvider() throws Exception {
        int calls = stub.requests().size();
        for (Object bad : List.of(Map.of(), Map.of("categoryId", 1), Map.of("resumeId", -1),
                Map.of("resumeId", 0), Map.of("resumeId", 1.5), Map.of("resumeId", "1"))) {
            send(owner, "/api/sessions", bad).andExpect(status().isBadRequest());
        }
        for (Object bad : List.of(Map.of(), Map.of("sessionId", 12, "questionId", 1),
                Map.of("sessionId", 12, "userAnswer", FIRST), Map.of("questionId", 1, "userAnswer", FIRST))) {
            send(owner, "/api/answers", bad).andExpect(status().isBadRequest());
        }
        for (Object bad : List.of(-1, 0, 1.1, "1", true)) {
            answer(owner, bad, 1, FIRST).andExpect(status().isBadRequest());
            answer(owner, 1, bad, FIRST).andExpect(status().isBadRequest());
        }
        for (Object bad : List.of("", " \t\n　", "\u00a0", "🚀".repeat(10_001), 123, true, List.of("x"), Map.of("x", 1))) {
            answer(owner, 1, 1, bad).andExpect(status().isBadRequest());
        }
        for (String path : List.of("/api/sessions", "/api/answers")) {
            for (String empty : List.of("", "null")) {
                mvc.perform(post(path).header("Authorization", owner).contentType(MediaType.APPLICATION_JSON)
                        .content(empty)).andExpect(status().isBadRequest());
            }
        }
        done(owner, 0).andExpect(status().isBadRequest());
        done(owner, -1).andExpect(status().isBadRequest());
        mvc.perform(patch("/api/sessions/not-a-number/done").header("Authorization", owner))
                .andExpect(status().isBadRequest());
        assertThat(stub.requests()).hasSize(calls);
        JsonNode s = start();
        stub.respondContent(FINAL);
        String max = "🚀".repeat(10_000);
        JsonNode one = created(answer(owner, s.path("id").asLong(), firstId(s), max));
        assertThat(one.path("userAnswer").asString()).isEqualTo(max);
        assertThat(one.path("followUpQuestion").isNull()).isTrue();
    }

    @ParameterizedTest
    @CsvSource({"limit,429", "json,502", "timeout,504", "disconnect,502", "insufficient,422",
            "auth,502", "provider,502", "refusal,502", "truncated,502"})
    void generationFailureLeavesNothingAndSameInputCanBeRetried(String failure, int expected) throws Exception {
        Counts before = counts();
        failProvider(failure);
        send(owner, "/api/sessions", Map.of("resumeId", resume)).andExpect(status().is(expected));
        assertThat(counts()).isEqualTo(before);
        start();
        assertThat(counts()).isEqualTo(before.plus(1, 5, 0));
    }

    @ParameterizedTest
    @CsvSource({"limit,429", "json,502", "timeout,504", "disconnect,502", "auth,502",
            "provider,502", "refusal,502", "truncated,502"})
    void evaluationFailureLeavesNothingAndSameInputCanBeRetried(String failure, int expected) throws Exception {
        JsonNode s = start();
        long sid = s.path("id").asLong(), qid = firstId(s);
        Counts before = counts();
        failProvider(failure);
        answer(owner, sid, qid, FIRST).andExpect(status().is(expected));
        assertThat(counts()).isEqualTo(before);
        stub.respondContent(FINAL);
        created(answer(owner, sid, qid, FIRST));
        assertThat(counts()).isEqualTo(before.plus(0, 0, 1));
    }

    @Test
    void generationWaitHoldsNoConnectionAndHasNotCreatedASession() throws Exception {
        Counts before = counts();
        CountDownLatch arrivals = new CountDownLatch(1), release = new CountDownLatch(1);
        stub.holdResponses(arrivals, release);
        var pool = Executors.newSingleThreadExecutor();
        try {
            var session = pool.submit(this::start);
            assertThat(arrivals.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(dataSource.getHikariPoolMXBean().getActiveConnections()).isZero();
            assertThat(counts()).isEqualTo(before);
            release.countDown();
            assertThat(session.get(5, TimeUnit.SECONDS).path("questions").size()).isEqualTo(5);
        } finally {
            release.countDown();
            stub.holdResponses(null, null);
            pool.shutdownNow();
        }
    }

    @Test
    void failedRootOrChildInsertRollsBackEarlierWrites() throws Exception {
        Counts before = counts();
        long previous = db.queryForObject("select coalesce(max(id), 0) from sessions", Long.class);
        db.execute("alter table questions add constraint flow_test_reject_fifth check " +
                "(session_id <= " + previous + " or question_position <> 5)");
        try {
            serveFiveRoots();
            send(owner, "/api/sessions", Map.of("resumeId", resume)).andExpect(status().isInternalServerError());
            assertThat(counts()).isEqualTo(before);
        } finally {
            db.execute("alter table questions drop constraint flow_test_reject_fifth");
        }

        JsonNode s = start();
        long sid = s.path("id").asLong(), qid = firstId(s);
        db.execute("alter table questions add constraint flow_test_reject_child " +
                "check (session_id <> " + sid + " or parent_question_id is null)");
        stub.respondContent(INITIAL);
        try {
            answer(owner, sid, qid, FIRST).andExpect(status().isInternalServerError());
            assertThat(counts()).isEqualTo(before.plus(1, 5, 0));
        } finally {
            db.execute("alter table questions drop constraint flow_test_reject_child");
        }
        created(answer(owner, sid, qid, FIRST));
        assertThat(counts()).isEqualTo(before.plus(1, 6, 1));
    }

    @Test
    void simultaneousSubmissionsReachingProviderCommitOnlyOneAnswerAndOneChild() throws Exception {
        JsonNode s = start();
        long sid = s.path("id").asLong(), qid = firstId(s);
        Counts before = counts();
        stub.respondContent(INITIAL);
        CountDownLatch arrivals = new CountDownLatch(2), release = new CountDownLatch(1);
        stub.holdResponses(arrivals, release);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var a = pool.submit(() -> answer(owner, sid, qid, FIRST).andReturn().getResponse().getStatus());
            var b = pool.submit(() -> answer(owner, sid, qid, FIRST).andReturn().getResponse().getStatus());
            assertThat(arrivals.await(2, TimeUnit.SECONDS)).isTrue();
            release.countDown();
            assertThat(List.of(a.get(5, TimeUnit.SECONDS), b.get(5, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(201, 409);
            assertThat(counts()).isEqualTo(before.plus(0, 1, 1));
        } finally {
            release.countDown();
            stub.holdResponses(null, null);
            pool.shutdownNow();
        }
    }

    @Test
    void completionDuringProviderWaitPreventsLateWrite() throws Exception {
        JsonNode s = start();
        long sid = s.path("id").asLong(), qid = firstId(s);
        Counts before = counts();
        stub.respondContent(INITIAL);
        CountDownLatch arrivals = new CountDownLatch(1), release = new CountDownLatch(1);
        stub.holdResponses(arrivals, release);
        var pool = Executors.newSingleThreadExecutor();
        try {
            var late = pool.submit(() -> answer(owner, sid, qid, FIRST).andReturn().getResponse().getStatus());
            assertThat(arrivals.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(dataSource.getHikariPoolMXBean().getActiveConnections()).isZero();
            done(owner, sid).andExpect(status().isOk());
            release.countDown();
            assertThat(late.get(5, TimeUnit.SECONDS)).isEqualTo(400);
            int calls = stub.requests().size();
            answer(owner, sid, qid, FIRST).andExpect(status().isBadRequest());
            done(owner, sid).andExpect(status().isBadRequest());
            assertThat(stub.requests()).hasSize(calls);
            assertThat(counts()).isEqualTo(before);
        } finally {
            release.countDown();
            stub.holdResponses(null, null);
            pool.shutdownNow();
        }
    }

    private void failProvider(String failure) {
        switch (failure) {
            case "limit" -> stub.respond(429, "private upstream error");
            case "auth" -> stub.respond(401, "wrong key");
            case "provider" -> stub.respond(503, "server failure");
            case "json" -> stub.respondContent("{");
            case "timeout" -> stub.respondSlowly("{}", Duration.ofSeconds(5), false);
            case "disconnect" -> stub.disconnect();
            case "insufficient" -> stub.respondContent("{\"status\":\"INSUFFICIENT_RESUME\",\"questions\":[]}");
            case "refusal" -> stub.respondContent("{}", "stop", "refused");
            case "truncated" -> stub.respondContent("{}", "length", null);
            default -> throw new AssertionError(failure);
        }
    }

    private String signup() throws Exception {
        var credentials = Map.of("email", UUID.randomUUID() + "@example.test", "password", "test-password");
        return "Bearer " + created(send("", "/api/auth/signup", credentials)).path("token").asString();
    }

    private JsonNode start() throws Exception {
        serveFiveRoots();
        return created(send(owner, "/api/sessions", Map.of("resumeId", resume)));
    }

    private void serveFiveRoots() {
        var roots = IntStream.rangeClosed(1, 5).mapToObj(i -> Map.of("content", "선택 근거 " + i,
                "sourceQuote", SOURCE, "intent", "이유 확인", "evaluationCriteria", List.of("근거"))).toList();
        stub.respondContent(json.writeValueAsString(Map.of("status", "OK", "questions", roots)));
    }

    private long firstId(JsonNode session) {
        return session.path("questions").get(0).path("id").asLong();
    }

    private JsonNode storedEvaluation(JsonNode response) {
        long id = response.path("id").asLong();
        return json.valueToTree(answers.findById(id).orElseThrow().getEvaluation());
    }

    private JsonNode lastContext() {
        var requests = stub.requests();
        return json.readTree(requests.get(requests.size() - 1).body().path("messages").get(1).path("content").asString());
    }

    private Counts counts() { return new Counts(sessions.count(), questions.count(), answers.count()); }

    record Counts(long sessions, long questions, long answers) {
        Counts plus(long s, long q, long a) { return new Counts(sessions + s, questions + q, answers + a); }
    }

    private JsonNode created(ResultActions result) throws Exception {
        return json.readTree(result.andExpect(status().isCreated()).andReturn().getResponse().getContentAsByteArray());
    }

    private JsonNode ok(ResultActions result) throws Exception {
        return json.readTree(result.andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
    }

    private void assertStoredAnswer(JsonNode question, String text, String evaluation, String stage) {
        assertThat(question.path("answers").size()).isEqualTo(1);
        JsonNode stored = question.path("answers").get(0);
        assertThat(stored.path("userAnswer").asString()).isEqualTo(text);
        assertThat(stored.path("evaluation")).isEqualTo(json.readTree(evaluation));
        assertThat(stored.path("evaluationStage").asString()).isEqualTo(stage);
        assertThat(stored.path("model").asString()).isEqualTo("openai/gpt-oss-120b");
        assertThat(stored.path("criteriaVersion").asString()).isEqualTo("resume-interview-v1");
    }

    private long legacySession(long ownerResume, long category) {
        long user = db.queryForObject("select user_id from resumes where id = ?", Long.class, ownerResume);
        db.update("insert into sessions (user_id, category_id, status, created_at) values (?, ?, 'DONE', current_timestamp)",
                user, category);
        return db.queryForObject("select id from sessions where user_id = ? and category_id = ?",
                Long.class, user, category);
    }

    private void legacyAnswer(long session, long question, String text, int score) {
        db.update("insert into answers (session_id, question_id, user_answer, feedback, score, created_at) "
                        + "values (?, ?, ?, '기존 평가', ?, current_timestamp)", session, question, text, score);
    }

    private ResultActions detail(String token, Object id) throws Exception {
        return mvc.perform(get("/api/sessions/{id}", id).header("Authorization", token));
    }

    private ResultActions done(String token, long session) throws Exception {
        return mvc.perform(patch("/api/sessions/{id}/done", session).header("Authorization", token));
    }

    private ResultActions answer(String token, Object session, Object question, Object text) throws Exception {
        return send(token, "/api/answers", Map.of("sessionId", session, "questionId", question, "userAnswer", text));
    }

    private ResultActions send(String token, String path, Object body) throws Exception {
        return mvc.perform(post(path).header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsBytes(body)));
    }
}
