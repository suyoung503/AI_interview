package com.interview.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.interview.entity.Session;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
public class SessionResponse {
    private final Long id;
    private final String categoryName;
    private final String status;
    private final LocalDateTime createdAt;
    private final List<QuestionResponse> questions;
    private final Long resumeId;
    private final String model;
    private final String criteriaVersion;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private final ResumeResponse resume;

    public SessionResponse(Session session, List<QuestionResponse> questions, ResumeResponse resume) {
        this.id = session.getId();
        this.categoryName = session.getCategory() == null ? null : session.getCategory().getName();
        this.status = session.getStatus().name();
        this.createdAt = session.getCreatedAt();
        this.questions = questions;
        this.resumeId = session.getResume() == null ? null : session.getResume().getId();
        this.model = session.getModel();
        this.criteriaVersion = session.getCriteriaVersion();
        this.resume = resume;
    }

    public SessionResponse(Session session, List<QuestionResponse> questions) {
        this(session, questions, null);
    }
// this로 위에 호출하되 question에 null들어가게 한거같음.
    public SessionResponse(Session session) {
        this(session, null);
    }
}
