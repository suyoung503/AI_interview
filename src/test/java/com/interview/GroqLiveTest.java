package com.interview;

import com.interview.dto.InterviewAiModels.EvaluationContext;
import com.interview.dto.InterviewAiModels.GeneratedQuestion;
import com.interview.exception.CustomException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@EnabledIfSystemProperty(named = "groq.live.confirmed", matches = "true")
@SpringBootTest(properties = {
        "spring.config.location=classpath:/application.properties,classpath:/application-test.yml",
        "spring.config.import=optional:file:${INTERVIEW_ENV_FILE:./.env}[.properties]",
        "spring.datasource.url=jdbc:h2:mem:groq-live;MODE=MySQL",
        "logging.level.root=INFO"
})
@ActiveProfiles("test")
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
class GroqLiveTest {

    private static final String REDIS_EXPERIENCE = "Spring Boot로 만든 가상 도서관 서비스에서 Redis 조회 캐시의 TTL을 설계했습니다.";
    static final String FOLLOW_UP_ANSWER =
            "도서 목록은 잠깐 오래된 정보여도 되지만 대출 재고는 정확해야 합니다. 조회 캐시와 대출 갱신을 분리했습니다.";
    private static final String RESUME = REDIS_EXPERIENCE + "\n" + """
            재고가 지연돼도 되는 도서 목록만 캐시하고, 최종 대출은 MySQL 트랜잭션으로 처리했습니다.
            동시 요청에서 중복 대출을 막으려고 조건부 갱신을 사용했습니다.
            부하 테스트에서 캐시 만료 직후 DB 요청이 몰려 TTL에 무작위 편차를 추가했습니다.
            Redis 장애 시 DB로 돌아가도록 하되 동시 조회 수를 제한했습니다.
            """;
    static final GeneratedQuestion REDIS_QUESTION = new GeneratedQuestion(
            "로컬 캐시 대신 Redis를 선택한 이유와 그 선택이 요구사항에 맞는 근거를 설명해 주세요.",
            REDIS_EXPERIENCE, "선택을 정당화하고 대안을 비교하는지 확인",
            List.of("기술적 정확성", "근거", "설명력"));

    static final String TECHNICAL_ERROR_ANSWER =
            "JVM 내부 로컬 캐시가 별개 서버들의 힙을 자동으로 동기화하고, Redis 캐시는 키가 영원히 만료되지 않습니다.";

    @TestConfiguration
    static class Config {

        @Bean @Primary
        GroqLiveClient confirmedLiveClient(@Value("${GROQ_API_KEY:}") String key) {
            assertTrue(!key.isBlank(), "GROQ_API_KEY를 로컬 비밀 설정에 준비해야 합니다.");
            return new GroqLiveClient(key, 6);
        }
    }

    @Autowired GroqLiveClient client;
    @Autowired MockMvc mvc;
    @Autowired JsonMapper json;
    private final Map<String, Object> evidence = new LinkedHashMap<>();

    @Test
    void generatesEvaluatesAndReloadsSyntheticInterviewWithControlledComparisons() throws Exception {
        GroqLiveEvidence.requireHttpGuards();
        evidence.put("completed", false);
        evidence.put("comprehensiveEvaluationPerformed", false);
        try {
            runInterview();
            evaluateControlledSamples();
            evidence.put("completed", true);
        } catch (CustomException e) {
            evidence.put("error", GroqLiveEvidence.failure(e.getErrorCode().getStatus().value(), e));
            throw new AssertionError("Groq 평가 실패: " + e.getErrorCode());
        } finally {
            evidence.put("attempts", client.attempts());
            Files.writeString(Path.of("target", "groq-live-evidence.json"),
                    json.writerWithDefaultPrettyPrinter().writeValueAsString(evidence));
        }
    }

    private void runInterview() throws Exception {
        var credentials = Map.of("email", "groq-live@example.test", "password", "synthetic-test-password");
        body(send("", "/api/auth/signup", credentials), 201);
        String token = "Bearer " + body(send("", "/api/auth/login", credentials), 200).path("token").asString();
        JsonNode resume = body(send(token, "/api/resumes", Map.of("text", RESUME)), 201);
        JsonNode session = body(send(token, "/api/sessions", Map.of("resumeId", resume.path("id").asLong())), 201);
        long sessionId = session.path("id").asLong();
        evidence.put("generatedSession", session);
        JsonNode roots = session.path("questions");
        assertThat(roots.size()).isEqualTo(5);
        for (JsonNode root : roots) {
            String quote = root.path("sourceQuote").asString();
            assertThat(quote).isNotBlank();
            assertThat(RESUME).contains(quote);
        }

        JsonNode original = answer(token, sessionId, roots.get(0).path("id").asLong(),
                "요구사항에서 어떤 조건을 우선했는지 아직 설명하지 않았습니다.");
        evidence.put("initialAnswer", original);
        assertThat(original.path("evaluationStage").asString()).isEqualTo("INITIAL");
        assertThat(original.path("score").isNull()).isTrue();
        assertThat(original.path("evaluation").isObject()).isTrue();

        JsonNode followUp = original.path("followUpQuestion");
        JsonNode combined = null;
        if (followUp.isObject()) {
            combined = answer(token, sessionId, followUp.path("id").asLong(), FOLLOW_UP_ANSWER);
            evidence.put("followUpAnswer", combined);
            assertThat(combined.path("evaluationStage").asString()).isEqualTo("COMPREHENSIVE");
            assertThat(combined.path("evaluation").isObject()).isTrue();
            assertThat(combined.path("followUpQuestion").isNull()).isTrue();
            evidence.put("comprehensiveEvaluationPerformed", true);
        }

        String detailPath = "/api/sessions/" + sessionId;
        JsonNode detail = body(mvc.perform(get(detailPath).header("Authorization", token)), 200);
        evidence.put("sessionHistory", detail);
        assertThat(detail.path("resume").path("text").asString()).isEqualTo(RESUME);
        JsonNode history = detail.path("questions");
        assertThat(history.size()).isEqualTo(combined == null ? 5 : 6);
        assertStoredAnswer(history.get(0), original);
        if (combined != null) {
            assertThat(history.get(1).path("parentQuestionId")).isEqualTo(roots.get(0).path("id"));
            assertStoredAnswer(history.get(1), combined);
        }

        body(mvc.perform(patch(detailPath + "/done").header("Authorization", token)), 200);
        JsonNode completed = body(mvc.perform(get(detailPath).header("Authorization", token)), 200);
        assertThat(completed.path("status").asString()).isEqualTo("DONE");
        assertThat(completed.path("questions")).isEqualTo(history);
    }

    private void assertStoredAnswer(JsonNode question, JsonNode expected) {
        JsonNode answers = question.path("answers");
        assertThat(answers.size()).isEqualTo(1);
        for (String field : List.of("id", "userAnswer", "evaluation", "evaluationStage",
                "model", "criteriaVersion", "followUpQuestion")) {
            assertThat(answers.get(0).path(field)).as(field).isEqualTo(expected.path(field));
        }
    }

    private void evaluateControlledSamples() {
        evidence.put("controlledQuestion", REDIS_QUESTION);
        var evaluations = new LinkedHashMap<String, Object>();
        evidence.put("controlledEvaluations", evaluations);
        // 같은 질문·기준으로 답변만 교체한다. 모델의 문구나 분류는 사람이 비교한다.
        for (var sample : List.of(
                Map.entry("비정석이지만 타당",
                        "이틀 안에 배포해야 해서 팀이 경험 있는 Redis를 썼습니다. 인스턴스 셋이 같은 인기 목록을 읽어"
                                + " TTL을 한 곳에서 관리하는 게 유리했고, 목록은 30초까지 지연돼도 됩니다."
                                + " 단일 인스턴스라면 로컬 캐시가 더 단순합니다. Redis 장애 때는 DB 조회 동시성을 제한합니다."),
                Map.entry("근거 부족", "Redis가 좋다고 해서 Redis를 썼습니다."),
                Map.entry("명백한 기술 오류", TECHNICAL_ERROR_ANSWER))) {
            var evaluation = client.evaluate(new EvaluationContext(REDIS_QUESTION, sample.getValue(), null, null, null));
            evaluations.put(sample.getKey(), Map.of("answer", sample.getValue(), "evaluation", evaluation));
        }
    }

    private JsonNode answer(String token, long sessionId, long questionId, String answer) throws Exception {
        return body(send(token, "/api/answers",
                Map.of("sessionId", sessionId, "questionId", questionId, "userAnswer", answer)), 201);
    }

    private ResultActions send(String token, String path, Object body) throws Exception {
        return mvc.perform(post(path).header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)));
    }

    private JsonNode body(ResultActions result, int expectedStatus) throws Exception {
        return GroqLiveEvidence.body(json, result, expectedStatus, evidence);
    }
}
