package com.interview.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Objects;

public final class InterviewAiModels {

    private InterviewAiModels() {}

    public enum TechnicalAccuracy { CORRECT, INCORRECT, NOT_ASSESSABLE }
    public enum Reasoning { SUFFICIENT, INSUFFICIENT, INVALID }
    public enum Clarity { CLEAR, UNCLEAR }
    public enum Axis { TECHNICAL_ACCURACY, REASONING, CLARITY }
    public enum EvidenceSource { ORIGINAL_ANSWER, FOLLOW_UP_ANSWER }

    public record AnswerEvidence(EvidenceSource source, String quote) {
        public AnswerEvidence {
            Objects.requireNonNull(source);
            Objects.requireNonNull(quote);
        }
    }

    public record AxisAssessment(Axis axis, List<AnswerEvidence> evidence,
                                 List<String> coveredPoints, List<String> improvements) {
        public AxisAssessment {
            Objects.requireNonNull(axis);
            evidence = List.copyOf(evidence);
            coveredPoints = List.copyOf(coveredPoints);
            improvements = List.copyOf(improvements);
        }
    }

    public record GeneratedQuestion(String content, String sourceQuote, String intent,
                                    List<String> evaluationCriteria) {
        public GeneratedQuestion {
            Objects.requireNonNull(content);
            Objects.requireNonNull(sourceQuote);
            Objects.requireNonNull(intent);
            evaluationCriteria = List.copyOf(evaluationCriteria);
        }
    }

    public record Evaluation(TechnicalAccuracy technicalAccuracy, Reasoning reasoning, Clarity clarity,
                             List<String> strengths, List<String> improvements, List<String> uncertainties,
                             String summary, String followUpQuestion,
                             @JsonInclude(JsonInclude.Include.NON_NULL) List<AxisAssessment> assessments,
                             @JsonInclude(JsonInclude.Include.NON_NULL) Axis followUpAxis) {
        public Evaluation(TechnicalAccuracy technicalAccuracy, Reasoning reasoning, Clarity clarity,
                          List<String> strengths, List<String> improvements, List<String> uncertainties,
                          String summary, String followUpQuestion) {
            this(technicalAccuracy, reasoning, clarity, strengths, improvements, uncertainties,
                    summary, followUpQuestion, null, null);
        }
        public Evaluation {
            Objects.requireNonNull(technicalAccuracy);
            Objects.requireNonNull(reasoning);
            Objects.requireNonNull(clarity);
            strengths = List.copyOf(strengths);
            improvements = List.copyOf(improvements);
            uncertainties = List.copyOf(uncertainties);
            Objects.requireNonNull(summary);
            assessments = assessments == null ? null : List.copyOf(assessments);
        }
    }

    // 초기 평가에는 마지막 세 필드가 없고, 꼬리 평가에는 원문과 초기 평가도 함께 전달한다.
    public record EvaluationContext(GeneratedQuestion originalQuestion, String originalAnswer,
                                    Evaluation initialEvaluation, String followUpQuestion, String followUpAnswer) {}
}
