package com.interview.dto;

import com.interview.dto.InterviewAiModels.Evaluation;
import com.interview.entity.Answer;
import com.interview.entity.Question;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class AnswerResponse {
    public enum EvaluationStage { INITIAL, COMPREHENSIVE }

    private final Long id;
    private final String questionContent;
    private final String userAnswer;
    private final String feedback;
    private final Integer score;
    private final LocalDateTime createdAt;

    private final Evaluation evaluation;
    private final EvaluationStage evaluationStage;
    private final String model;
    private final String criteriaVersion;
    private final QuestionResponse followUpQuestion;

    public AnswerResponse(Answer answer, Question followUp) {
        Question question = answer.getQuestion();
        this.evaluation = answer.getEvaluation();
        this.evaluationStage = evaluation == null ? null
                : (question.getParentQuestion() == null ? EvaluationStage.INITIAL : EvaluationStage.COMPREHENSIVE);
        this.model = answer.getModel();
        this.criteriaVersion = answer.getCriteriaVersion();
        this.followUpQuestion = followUp == null ? null : new QuestionResponse(followUp);
        this.id = answer.getId();
        this.questionContent = question.getContent();
        this.userAnswer = answer.getUserAnswer();
        this.feedback = answer.getFeedback();
        this.score = answer.getScore();
        this.createdAt = answer.getCreatedAt();
    }
}
