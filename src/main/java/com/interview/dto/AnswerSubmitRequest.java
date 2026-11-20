package com.interview.dto;

import com.fasterxml.jackson.annotation.JsonSetter;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import org.hibernate.validator.constraints.CodePointLength;
import tools.jackson.databind.JsonNode;

@Getter
public class AnswerSubmitRequest {

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
        sessionId = RequestIds.integer(value);
    }

    @JsonSetter("questionId")
    private void setQuestionId(JsonNode value) {
        questionId = RequestIds.integer(value);
    }

    @JsonSetter("userAnswer")
    private void setUserAnswer(JsonNode value) {
        userAnswer = value == null ? null : value.stringValue(null);
    }
}
