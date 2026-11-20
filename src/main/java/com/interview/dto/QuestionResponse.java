package com.interview.dto;

import com.interview.entity.Question;
import lombok.Getter;

import java.util.List;

@Getter
public class QuestionResponse {
    private final Long id;
    private final String content;

    private final Integer position;
    private final String sourceQuote;
    private final String intent;
    private final List<String> evaluationCriteria;
    private final Long parentQuestionId;

    public QuestionResponse(Question question) {
        this.id = question.getId();
        this.content = question.getContent();
        this.position = question.getPosition();
        this.sourceQuote = question.getSourceQuote();
        this.intent = question.getIntent();
        this.evaluationCriteria = question.getEvaluationCriteria();
        this.parentQuestionId = question.getParentQuestion() == null ? null : question.getParentQuestion().getId();
    }
}
