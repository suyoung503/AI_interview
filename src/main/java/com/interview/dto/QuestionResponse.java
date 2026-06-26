package com.interview.dto;

import com.interview.entity.Question;
import lombok.Getter;

@Getter
public class QuestionResponse {
    private final Long id;
    private final String content;

    public QuestionResponse(Question question) {
        this.id = question.getId();
        this.content = question.getContent();
    }
}
