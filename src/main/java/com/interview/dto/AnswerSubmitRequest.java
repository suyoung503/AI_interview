package com.interview.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class AnswerSubmitRequest {
    private Long sessionId;
    private Long questionId;
    private String userAnswer;
}
