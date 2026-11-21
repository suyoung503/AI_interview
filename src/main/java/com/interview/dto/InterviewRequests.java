package com.interview.dto;

import com.fasterxml.jackson.annotation.JsonSetter;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.CodePointLength;
import tools.jackson.databind.JsonNode;

public final class InterviewRequests {

    private InterviewRequests() {}

    @Getter
    @NoArgsConstructor
    public static class ResumeCreateRequest {

        @NotNull
        @Pattern(regexp = "(?Us)\\s*+\\S.*")
        @CodePointLength(max = 20_000)
        private String text;

        @JsonSetter("text")
        private void setText(JsonNode value) {
            this.text = value == null ? null : value.stringValue(null);
        }
    }

    @Getter
    public static class SessionCreateRequest {

        @NotNull @Positive
        private Long resumeId;

        @JsonSetter("resumeId")
        private void setResumeId(JsonNode value) {
            resumeId = integer(value);
        }
    }

    @Getter
    public static class AnswerSubmitRequest {

        @NotNull @Positive
        private Long sessionId;
        @NotNull @Positive
        private Long questionId;

        @NotNull
        @Pattern(regexp = "(?Us)\\s*+\\S.*")
        @CodePointLength(max = 10_000)
        private String userAnswer;

        @JsonSetter("sessionId")
        private void setSessionId(JsonNode value) {
            sessionId = integer(value);
        }

        @JsonSetter("questionId")
        private void setQuestionId(JsonNode value) {
            questionId = integer(value);
        }

        @JsonSetter("userAnswer")
        private void setUserAnswer(JsonNode value) {
            userAnswer = value == null ? null : value.stringValue(null);
        }
    }

    // JSON 숫자 1.5나 문자열 "1"이 실제 ID로 강제 변환되지 않게 한다.
    private static Long integer(JsonNode value) {
        return value != null && value.isIntegralNumber() && value.canConvertToLong()
                ? value.longValue() : null;
    }
}
