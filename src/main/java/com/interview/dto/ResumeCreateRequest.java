package com.interview.dto;

import com.fasterxml.jackson.annotation.JsonSetter;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.CodePointLength;
import tools.jackson.databind.JsonNode;

@Getter
@NoArgsConstructor
public class ResumeCreateRequest {

    @NotNull
    @Pattern(regexp = "(?Us)\\s*+\\S.*")
    @CodePointLength(max = 20_000)
    private String text;

    @JsonSetter("text")
    private void setText(JsonNode value) {
        this.text = value == null ? null : value.stringValue(null);
    }
}
