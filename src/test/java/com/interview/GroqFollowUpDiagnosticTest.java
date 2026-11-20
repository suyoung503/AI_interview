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
            "2026-11-20-resume-interview-coach", "task-6-groq-live-evidence.json");
    private static final String SYNTHETIC_SHA256 = "3d9302ff170a2eb271bdf0443b6543680132110d119ddc0900bef45b47699a8c";

    @Configuration(proxyBeanMethods = false)
    static class Config {}

    @Value("${GROQ_API_KEY:}") String key;

    @Test
    void diagnosesOneDirectCallForTheOriginalSyntheticFollowUp() throws Exception {
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
            Files.writeString(Path.of("target", "groq-followup-diagnostic-evidence.json"),
                    json.writerWithDefaultPrettyPrinter().writeValueAsString(evidence));
        }
    }

    private EvaluationContext restoreSyntheticContext(JsonMapper json) throws Exception {
        byte[] artifact = Files.readAllBytes(SYNTHETIC_EVIDENCE);
        // Prevent accidentally sending an edited fixture or a production interview.
        String sha256 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(artifact));
        assertEquals(SYNTHETIC_SHA256, sha256, "승인한 최초 가상 실검증 결과와 같은 파일이어야 합니다.");
        var saved = json.readTree(artifact);
        var root = saved.path("generatedSession").path("questions").get(0);
        var initial = saved.path("initialAnswer");
        var criteria = new ArrayList<String>();
        root.path("evaluationCriteria").forEach(item -> criteria.add(item.asString()));
        return new EvaluationContext(new GeneratedQuestion(root.path("content").asString(),
                root.path("sourceQuote").asString(), root.path("intent").asString(), criteria),
                initial.path("userAnswer").asString(), json.treeToValue(initial.path("evaluation"), Evaluation.class),
                initial.path("followUpQuestion").path("content").asString(), GroqLiveTest.FOLLOW_UP_ANSWER);
    }
}
