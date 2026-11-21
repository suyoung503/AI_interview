package com.interview.live;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.interview.exception.AiProviderException;
import com.interview.exception.CustomException;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

final class GroqLiveEvidence {

    private GroqLiveEvidence() {}

    static JsonNode body(JsonMapper json, ResultActions result, int expectedStatus, Map<String, Object> evidence)
            throws Exception {
        var mvcResult = result.andReturn();
        int actualStatus = mvcResult.getResponse().getStatus();
        if (actualStatus != expectedStatus) {
            evidence.put("error", failure(actualStatus, mvcResult.getResolvedException()));
        }
        result.andExpect(status().is(expectedStatus));
        return json.readTree(mvcResult.getResponse().getContentAsByteArray());
    }

    static Failure failure(int status, Throwable error) {
        var code = error instanceof CustomException customException
                ? customException.getErrorCode().name() : "UNAVAILABLE";
        // Do not serialize the exception, messages, causes, requests, headers or response bodies.
        var frames = error == null ? List.<Frame>of() : Arrays.stream(error.getStackTrace())
                .filter(frame -> frame.getClassName().startsWith("com.interview."))
                .map(frame -> new Frame(frame.getClassName(), frame.getMethodName(), frame.getLineNumber()))
                .toList();
        Integer upstreamStatus = error instanceof AiProviderException provider ? provider.getUpstreamStatus() : null;
        return new Failure(status, code, frames, upstreamStatus);
    }

    static void requireHttpGuards() {
        assertThat(System.getProperty("jdk.httpclient.redirects.retrylimit"))
                .as("README의 JVM 옵션으로 HTTP 재시도를 금지합니다.").isEqualTo("1");
        assertTrue(System.getProperty("jdk.httpclient.HttpClient.log", "").isEmpty(),
                "HTTP 요청 로그를 비활성화해야 합니다.");
    }

    static void requireProbeGuards(Properties options) {
        assertThat(options.getProperty("jdk.httpclient.disableRetryConnect")).isEqualTo("true");
        assertThat(options.getProperty("jdk.httpclient.enableAllMethodRetry")).isEqualTo("false");
        assertThat(options.getProperty("surefire.rerunFailingTestsCount")).isEqualTo("0");
    }

    record Failure(int status, String errorCode, List<Frame> stackFrames,
                   @JsonInclude(JsonInclude.Include.NON_NULL) Integer upstreamStatus) {}
    record Frame(String className, String methodName, int lineNumber) {}
}
