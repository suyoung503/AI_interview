package com.interview.entity;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

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
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

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

    public void done() {
        this.status = SessionStatus.DONE;
    }
}
