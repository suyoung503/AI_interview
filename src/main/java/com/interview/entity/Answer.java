package com.interview.entity;

import com.interview.dto.InterviewAiModels.Evaluation;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.Objects;

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

    @Convert(converter = InterviewJsonConverters.EvaluationConverter.class)
    @Column(columnDefinition = "MEDIUMTEXT", updatable = false)
    private Evaluation evaluation;

    @Column(length = 100, updatable = false)
    private String model;

    @Column(name = "criteria_version", length = 50, updatable = false)
    private String criteriaVersion;

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

    public static Answer evaluated(Question question, String userAnswer, Evaluation evaluation,
                                   String model, String criteriaVersion) {
        Session session = Objects.requireNonNull(question).getSession();
        if (session == null) {
            throw new IllegalArgumentException("Structured evaluations require a session question");
        }
        Answer answer = new Answer(session, question, userAnswer, null, null);
        answer.evaluation = Objects.requireNonNull(evaluation);
        answer.model = Objects.requireNonNull(model);
        answer.criteriaVersion = Objects.requireNonNull(criteriaVersion);
        return answer;
    }
}
