package com.interview.live;

import com.interview.dto.InterviewAiModels.EvaluationContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Path;

@EnabledIfSystemProperty(named = "groq.technical.probe.confirmed", matches = "true")
class GroqTechnicalFeedbackProbeTest extends GroqDiagnosticSupport {

    @Test
    void recordsOneTechnicalCorrectionForHumanReview() throws Exception {
        GroqLiveEvidence.requireHttpGuards();
        GroqLiveEvidence.requireProbeGuards(System.getProperties());
        var context = new EvaluationContext(GroqLiveTest.REDIS_QUESTION, GroqLiveTest.TECHNICAL_ERROR_ANSWER,
                null, null, null);
        recordOneEvaluation(JsonMapper.builder().build(), context, Path.of("target", "groq-technical-feedback-evidence.json"));
    }
}
