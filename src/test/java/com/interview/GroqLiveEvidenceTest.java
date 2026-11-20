package com.interview;

import com.interview.exception.CustomException;
import com.interview.exception.ErrorCode;
import com.interview.exception.GlobalExceptionHandler;
import com.interview.service.GroqService;
import com.interview.support.GroqStub;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

class GroqLiveEvidenceTest {

    @ParameterizedTest
    @ValueSource(ints = {400, 503})
    void recordsOnlyUpstreamStatusWithoutChangingPublicFailure(int upstreamStatus) throws Exception {
        try (var stub = new GroqStub()) {
            stub.respond(upstreamStatus, "dummy-provider-body-marker");
            var client = new GroqService("dummy-provider-key", stub.endpoint(), Duration.ofSeconds(1),
                    Duration.ofSeconds(3));
            var json = JsonMapper.builder().build();

            assertThatThrownBy(() -> client.generateQuestions("dummy-input-marker"))
                    .isInstanceOfSatisfying(CustomException.class, e -> {
                        assertThat(e).hasNoCause().hasMessage(ErrorCode.AI_BAD_RESPONSE.getMessage());
                        var response = new GlobalExceptionHandler().handleCustomException(e);
                        assertThat(response.getStatusCode().value()).isEqualTo(502);
                        var publicBody = json.valueToTree(response.getBody());
                        assertThat(publicBody).isEqualTo(json.valueToTree(Map.of("status", 502, "message", e.getMessage())));
                        var failure = GroqLiveEvidence.failure(502, e);
                        var artifact = json.valueToTree(failure);
                        assertThat(artifact.toString()).doesNotContain("dummy-input-marker", "dummy-provider-key",
                                "dummy-provider-body-marker", "message", "cause");
                        assertThat(artifact.path("errorCode").asString()).isEqualTo("AI_BAD_RESPONSE");
                        assertThat(artifact.path("upstreamStatus").asInt()).isEqualTo(upstreamStatus);
                    });
        }
    }

    @Test
    void preservesOnlyErrorCodeAndFrameMetadataBeforeUnexpectedStatusFails() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new SyntheticFailure())
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        var json = JsonMapper.builder().build();
        var evidence = new LinkedHashMap<String, Object>();
        var response = mvc.perform(post("/synthetic-failure")
                .header("Authorization", "Bearer dummy-auth-marker")
                .contentType(MediaType.TEXT_PLAIN).content("dummy-input-marker"));

        assertThatThrownBy(() -> GroqLiveEvidence.body(json, response, 201, evidence))
                .isInstanceOf(AssertionError.class).hasMessageContaining("502");
        var artifact = json.writeValueAsString(evidence);
        assertThat(artifact).doesNotContain("dummy-input-marker", "dummy-auth-marker",
                "dummy-provider-body-marker", "message", "cause", "fileName");
        var error = json.readTree(artifact).path("error");
        assertThat(error.has("upstreamStatus")).isFalse();
        assertThat(error.path("status").asInt()).isEqualTo(502);
        assertThat(error.path("errorCode").asString()).isEqualTo("AI_BAD_RESPONSE");
        var frame = error.path("stackFrames").get(0);
        assertThat(frame.path("className").asString()).endsWith("GroqLiveEvidenceTest$SyntheticFailure");
        assertThat(frame.path("methodName").asString()).isEqualTo("simulatedProviderFailure");
        assertThat(frame.path("lineNumber").asInt()).isPositive();
        assertThat(frame.size()).isEqualTo(3);
    }

    @RestController
    static class SyntheticFailure {

        @PostMapping("/synthetic-failure")
        void simulatedProviderFailure(@RequestBody String input, @RequestHeader("Authorization") String auth) {
            var failure = new CustomException(ErrorCode.AI_BAD_RESPONSE) {
                @Override public String getMessage() { return input + " " + auth; }
            };
            failure.initCause(new IllegalArgumentException("dummy-provider-body-marker"));
            throw failure;
        }
    }
}
