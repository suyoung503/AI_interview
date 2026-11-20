package com.interview.dto;

import com.fasterxml.jackson.annotation.JsonSetter;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import tools.jackson.databind.JsonNode;

@Getter
public class SessionCreateRequest {

    @NotNull @Positive
    private Long resumeId;

    @JsonSetter("resumeId")
    private void setResumeId(JsonNode value) {
        resumeId = RequestIds.integer(value);
    }
}
