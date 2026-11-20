package com.interview.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.interview.entity.Resume;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class ResumeResponse {

    private final Long id;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private final String text;

    private final LocalDateTime createdAt;

    public ResumeResponse(Resume resume) {
        this.id = resume.getId();
        this.text = resume.getText();
        this.createdAt = resume.getCreatedAt();
    }

    public ResumeResponse(Long id, LocalDateTime createdAt) {
        this.id = id;
        this.text = null;
        this.createdAt = createdAt;
    }
}
