package com.interview;

import com.interview.dto.InterviewAiModels.EvaluationContext;
import com.interview.dto.InterviewAiModels.GeneratedQuestion;
import com.interview.support.GroqStub;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GroqLiveClientTest {

    @Test
    void permitsSixRequestsThenStopsBothGenerationAndEvaluationBeforeHttp() throws Exception {
        try (var stub = new GroqStub()) {
            stub.respondContent("""
                    {"technicalAccuracy":"CORRECT","reasoning":"SUFFICIENT","clarity":"CLEAR",
                     "strengths":[],"improvements":[],"uncertainties":[],"summary":"점검용","followUpQuestion":null}
                    """);
            var client = new GroqLiveClient("dummy-key", stub.endpoint());
            var context = new EvaluationContext(new GeneratedQuestion("근거는?", "캐시", "선택 이유",
                    List.of("근거")), "가상 답변", null, null, null);

            for (int i = 0; i < 6; i++) {
                client.evaluate(context);
            }
            assertThatThrownBy(() -> client.evaluate(context)).isInstanceOf(AssertionError.class);
            assertThatThrownBy(() -> client.generateQuestions("캐시")).isInstanceOf(AssertionError.class);
            assertThat(stub.requests()).hasSize(6);
        }
    }
}
