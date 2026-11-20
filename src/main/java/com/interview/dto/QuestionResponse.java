package com.interview.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.interview.entity.Question;
import lombok.Getter;

import java.util.List;

@Getter
public class QuestionResponse {
    private final Long id;
    private final String content;
    private final String categoryName;

    private final Integer position;
    private final String sourceQuote;
    private final String intent;
    private final List<String> evaluationCriteria;
    private final Long parentQuestionId;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private final List<AnswerResponse> answers;

    public QuestionResponse(Question question) {
        this(question, null);
    }

    public QuestionResponse(Question question, List<AnswerResponse> answers) {
        this.id = question.getId();
        this.content = question.getContent();
        this.categoryName = question.getCategory() == null ? null : question.getCategory().getName();
        this.position = question.getPosition();
        this.sourceQuote = question.getSourceQuote();
        this.intent = question.getIntent();
        this.evaluationCriteria = question.getEvaluationCriteria();
        this.parentQuestionId = question.getParentQuestion() == null ? null : question.getParentQuestion().getId();
        this.answers = answers;
    }
}
