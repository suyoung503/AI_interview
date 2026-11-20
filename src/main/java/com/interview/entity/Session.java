package com.interview.entity;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "sessions")
@Getter
@NoArgsConstructor
public class Session {

    public enum SessionStatus { IN_PROGRESS, DONE }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resume_id", updatable = false,
            foreignKey = @ForeignKey(name = "fk_interview_sessions_resume"))
    private Resume resume;

    @Column(length = 100, updatable = false)
    private String model;

    @Column(name = "criteria_version", length = 50, updatable = false)
    private String criteriaVersion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SessionStatus status;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Builder
    public Session(User user, Category category) {
        this.user = user;
        this.category = category;
        this.status = SessionStatus.IN_PROGRESS;
        this.createdAt = LocalDateTime.now();
    }

    public static Session forResume(Resume resume, String model, String criteriaVersion) {
        Session session = new Session(Objects.requireNonNull(resume).getUser(), null);
        session.resume = resume;
        session.model = Objects.requireNonNull(model);
        session.criteriaVersion = Objects.requireNonNull(criteriaVersion);
        return session;
    }

    public void done() {
        this.status = SessionStatus.DONE;
    }
}
