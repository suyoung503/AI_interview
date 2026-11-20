package com.interview.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.validator.constraints.CodePointLength;

@Getter
@NoArgsConstructor
public class ResumeCreateRequest {

    @NotNull
    @Pattern(regexp = "(?Us)\\s*+\\S.*")
    @CodePointLength(max = 20_000)
    private String text;
}
