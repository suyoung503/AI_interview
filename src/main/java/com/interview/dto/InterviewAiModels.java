package com.interview.dto;

import java.util.List;
import java.util.Objects;

public final class InterviewAiModels {

    private InterviewAiModels() {}

    public enum TechnicalAccuracy { CORRECT, INCORRECT, NOT_ASSESSABLE }
    public enum Reasoning { SUFFICIENT, INSUFFICIENT, INVALID }
    public enum Clarity { CLEAR, UNCLEAR }

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
                             String summary, String followUpQuestion) {
        public Evaluation {
            Objects.requireNonNull(technicalAccuracy);
            Objects.requireNonNull(reasoning);
            Objects.requireNonNull(clarity);
            strengths = List.copyOf(strengths);
            improvements = List.copyOf(improvements);
            uncertainties = List.copyOf(uncertainties);
            Objects.requireNonNull(summary);
        }
    }

    // 초기 평가에는 마지막 세 필드가 없고, 꼬리 평가에는 원문과 초기 평가도 함께 전달한다.
    public record EvaluationContext(GeneratedQuestion originalQuestion, String originalAnswer,
                                    Evaluation initialEvaluation, String followUpQuestion, String followUpAnswer) {}
}
