package com.interview;

import com.interview.dto.InterviewAiModels.Evaluation;
import com.interview.dto.InterviewAiModels.EvaluationContext;
import com.interview.dto.InterviewAiModels.GeneratedQuestion;
import com.interview.exception.CustomException;
import com.interview.service.GroqService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfSystemProperty(named = "groq.followup.diagnostic.confirmed", matches = "true")
@SpringBootTest(classes = GroqFollowUpDiagnosticTest.Config.class, webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {"spring.config.location=classpath:/application.properties",
                "spring.config.import=optional:file:${INTERVIEW_ENV_FILE:./.env}[.properties]",
                "logging.level.root=INFO"})
@ActiveProfiles("test")
class GroqFollowUpDiagnosticTest {

    private static final Path SYNTHETIC_EVIDENCE = Path.of(".superpowers", "sdd",
            "2026-11-20-resume-interview-coach", "task-7-final-groq-live-evidence.json");
    private static final String SYNTHETIC_SHA256 = "7141dd0a3ade7228b41d3d624136cd987a810dc9620bb59d7f823c5b82dd57c2";

    @Configuration(proxyBeanMethods = false)
    static class Config {}

    @Value("${GROQ_API_KEY:}") String key;

    @Test
    void diagnosesOneDirectCallForTheV2SyntheticFollowUp() throws Exception {
        GroqLiveEvidence.requireHttpGuards();
        var json = JsonMapper.builder().build();
        var context = restoreSyntheticContext(json);
        assertTrue(!key.isBlank(), "GROQ_API_KEY를 로컬 비밀 설정에 준비해야 합니다.");
        var client = new GroqService(key);

        var evidence = new LinkedHashMap<String, Object>();
        evidence.put("attempts", 1);
        try {
            evidence.put("evaluation", client.evaluate(context));
        } catch (RuntimeException e) {
            int status = e instanceof CustomException custom ? custom.getErrorCode().getStatus().value() : 500;
            var failure = GroqLiveEvidence.failure(status, e);
            evidence.put("error", failure);
            throw new AssertionError("Groq 꼬리 평가 실패: " + failure.errorCode());
        } finally {
            Files.writeString(Path.of("target", "groq-upstream-diagnostic-evidence.json"),
                    json.writerWithDefaultPrettyPrinter().writeValueAsString(evidence));
        }
    }

    private EvaluationContext restoreSyntheticContext(JsonMapper json) throws Exception {
        byte[] artifact = Files.readAllBytes(SYNTHETIC_EVIDENCE);
        // Prevent accidentally sending an edited fixture or a production interview.
        String sha256 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(artifact));
        assertEquals(SYNTHETIC_SHA256, sha256, "승인한 v2 가상 실검증 결과와 같은 파일이어야 합니다.");
        var saved = json.readTree(artifact);
        var session = saved.path("generatedSession");
        var initial = saved.path("initialAnswer");
        assertEquals("resume-interview-v2", session.path("criteriaVersion").asString());
        assertEquals("resume-interview-v2", initial.path("criteriaVersion").asString());
        var root = session.path("questions").get(0);
        var criteria = new ArrayList<String>();
        root.path("evaluationCriteria").forEach(item -> criteria.add(item.asString()));
        return new EvaluationContext(new GeneratedQuestion(root.path("content").asString(),
                root.path("sourceQuote").asString(), root.path("intent").asString(), criteria),
                initial.path("userAnswer").asString(), json.treeToValue(initial.path("evaluation"), Evaluation.class),
                initial.path("followUpQuestion").path("content").asString(), GroqLiveTest.FOLLOW_UP_ANSWER);
    }
}
