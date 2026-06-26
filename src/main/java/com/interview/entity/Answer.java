package com.interview.entity;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "answers")
@Getter
@NoArgsConstructor
public class Answer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private Session session;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;

    @Column(name = "user_answer", columnDefinition = "TEXT")
    private String userAnswer;

    @Column(columnDefinition = "TEXT")
    private String feedback;

    private Integer score;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Builder
    public Answer(Session session, Question question, String userAnswer, String feedback, Integer score) {
        this.session = session;
        this.question = question;
        this.userAnswer = userAnswer;
        this.feedback = feedback;
        this.score = score;
        this.createdAt = LocalDateTime.now();
    }
}
