package com.interview.dto;

import com.interview.entity.Answer;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class AnswerResponse {
    private final Long id;
    private final String questionContent;
    private final String userAnswer;
    private final String feedback;
    private final Integer score;
    private final LocalDateTime createdAt;

    public AnswerResponse(Answer answer) {
        this.id = answer.getId();
        this.questionContent = answer.getQuestion().getContent();
        this.userAnswer = answer.getUserAnswer();
        this.feedback = answer.getFeedback();
        this.score = answer.getScore();
        this.createdAt = answer.getCreatedAt();
    }
}
