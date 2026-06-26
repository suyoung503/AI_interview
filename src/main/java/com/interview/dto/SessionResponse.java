package com.interview.dto;

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

    public SessionResponse(Session session, List<QuestionResponse> questions) {
        this.id = session.getId();
        this.categoryName = session.getCategory().getName();
        this.status = session.getStatus().name();
        this.createdAt = session.getCreatedAt();
        this.questions = questions;
    }
// this로 위에 호출하되 question에 null들어가게 한거같음.
    public SessionResponse(Session session) {
        this(session, null);
    }
}
