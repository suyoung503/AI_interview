package com.interview.dto;

import com.interview.dto.InterviewAiModels.Evaluation;
import com.interview.entity.Answer;
import com.interview.entity.Question;
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

    private final Evaluation evaluation;
    private final QuestionResponse followUpQuestion;

    public AnswerResponse(Answer answer, Question followUp) {
        this.evaluation = answer.getEvaluation();
        this.followUpQuestion = followUp == null ? null : new QuestionResponse(followUp);
        this.id = answer.getId();
        this.questionContent = answer.getQuestion().getContent();
        this.userAnswer = answer.getUserAnswer();
        this.feedback = answer.getFeedback();
        this.score = answer.getScore();
        this.createdAt = answer.getCreatedAt();
    }
}
