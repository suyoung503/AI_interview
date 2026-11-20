package com.interview.entity;

import com.interview.dto.InterviewAiModels.GeneratedQuestion;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Objects;

@Entity
@Table(name = "questions", indexes = @Index(name = "ix_questions_session_position",
        columnList = "session_id, question_position"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Question {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", updatable = false,
            foreignKey = @ForeignKey(name = "fk_interview_questions_session"))
    private Session session;

    @Column(name = "question_position", updatable = false)
    private Integer position;

    @Column(name = "source_quote", columnDefinition = "MEDIUMTEXT", updatable = false)
    private String sourceQuote;

    @Column(columnDefinition = "TEXT", updatable = false)
    private String intent;

    @Convert(converter = InterviewJsonConverters.CriteriaConverter.class)
    @Column(name = "evaluation_criteria", columnDefinition = "MEDIUMTEXT", updatable = false)
    private List<String> evaluationCriteria;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_question_id", updatable = false,
            foreignKey = @ForeignKey(name = "fk_interview_questions_parent"))
    private Question parentQuestion;

    public static Question root(Session session, int position, GeneratedQuestion generated) {
        requireResumeSession(session);
        Question question = new Question(session, position, generated.content());
        question.sourceQuote = generated.sourceQuote();
        question.intent = generated.intent();
        question.evaluationCriteria = generated.evaluationCriteria();
        return question;
    }

    public static Question followUp(Session session, Question parent, String content) {
        requireResumeSession(session);
        if (parent == null || parent.getParentQuestion() != null || !sameSession(session, parent.getSession())) {
            throw new IllegalArgumentException("A follow-up requires a root question from the same session");
        }
        Question question = new Question(session, parent.getPosition(), content);
        question.parentQuestion = parent;
        return question;
    }

    private Question(Session session, int position, String content) {
        this.session = session;
        this.position = position;
        this.content = Objects.requireNonNull(content);
    }

    public String getSourceQuote() {
        return parentQuestion == null ? sourceQuote : parentQuestion.getSourceQuote();
    }

    public String getIntent() {
        return parentQuestion == null ? intent : parentQuestion.getIntent();
    }

    public List<String> getEvaluationCriteria() {
        return parentQuestion == null ? evaluationCriteria : parentQuestion.getEvaluationCriteria();
    }

    private static void requireResumeSession(Session session) {
        if (session == null || session.getResume() == null) {
            throw new IllegalArgumentException("Generated questions require a resume session");
        }
    }

    private static boolean sameSession(Session session, Session parentSession) {
        return session == parentSession || (parentSession != null && session.getId() != null
                && session.getId().equals(parentSession.getId()));
    }
}
