package com.interview;

import com.interview.dto.InterviewAiModels.EvaluationContext;
import com.interview.exception.CustomException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = GroqDiagnosticSupport.Config.class, webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {"spring.config.location=classpath:/application.properties",
                "spring.config.import=optional:file:${INTERVIEW_ENV_FILE:./.env}[.properties]",
                "logging.level.root=INFO"})
@ActiveProfiles("test")
abstract class GroqDiagnosticSupport {
    @Configuration(proxyBeanMethods = false)
    static class Config {}

    @Value("${GROQ_API_KEY:}") private String key;

    void recordOneEvaluation(JsonMapper json, EvaluationContext context, Path output) throws Exception {
        assertTrue(!key.isBlank(), "GROQ_API_KEY를 로컬 비밀 설정에 준비해야 합니다.");
        var client = new GroqLiveClient(key, 1);
        var evidence = new LinkedHashMap<String, Object>();
        evidence.put("model", client.getModel());
        evidence.put("criteriaVersion", client.getCriteriaVersion());
        try {
            evidence.put("evaluation", client.evaluate(context));
        } catch (RuntimeException e) {
            int status = e instanceof CustomException custom ? custom.getErrorCode().getStatus().value() : 500;
            var failure = GroqLiveEvidence.failure(status, e);
            evidence.put("error", failure);
            throw new AssertionError("Groq 평가 실패: " + failure.errorCode());
        } finally {
            evidence.put("attempts", client.attempts());
            Files.writeString(output, json.writerWithDefaultPrettyPrinter().writeValueAsString(evidence));
        }
    }
}
