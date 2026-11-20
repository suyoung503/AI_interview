package com.interview;

import com.interview.exception.CustomException;
import com.interview.exception.ErrorCode;
import com.interview.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.json.JsonMapper;

import java.util.LinkedHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

class GroqLiveEvidenceTest {

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
