package com.interview;

import com.interview.dto.InterviewAiModels.Evaluation;
import com.interview.dto.InterviewAiModels.EvaluationContext;
import com.interview.dto.InterviewAiModels.GeneratedQuestion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;

@EnabledIfSystemProperty(named = "groq.followup.diagnostic.confirmed", matches = "true")
class GroqFollowUpDiagnosticTest extends GroqDiagnosticSupport {

    private static final Path SYNTHETIC_EVIDENCE = Path.of(".superpowers", "sdd",
            "2026-11-20-resume-interview-coach", "task-7-final-groq-live-evidence.json");
    private static final String SYNTHETIC_SHA256 = "7141dd0a3ade7228b41d3d624136cd987a810dc9620bb59d7f823c5b82dd57c2";

    @Test
    void diagnosesOneDirectCallForTheV2SyntheticFollowUp() throws Exception {
        GroqLiveEvidence.requireHttpGuards();
        var json = JsonMapper.builder().build();
        recordOneEvaluation(json, restoreSyntheticContext(json), Path.of("target", "groq-upstream-diagnostic-evidence.json"));
    }

    private EvaluationContext restoreSyntheticContext(JsonMapper json) throws Exception {
        byte[] artifact = Files.readAllBytes(SYNTHETIC_EVIDENCE);
        // Prevent accidentally sending an edited fixture or a production interview.
        String sha256 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(artifact));
        assertEquals(SYNTHETIC_SHA256, sha256, "승인한 v2 가상 실검증 결과와 같은 파일이어야 합니다.");
        var saved = json.readTree(artifact);
        var session = saved.path("generatedSession");
        var initial = saved.path("initialAnswer");
        assertEquals("resume-interview-v2", session.path("criteriaVersion").asString());
        assertEquals("resume-interview-v2", initial.path("criteriaVersion").asString());
        var root = session.path("questions").get(0);
        var criteria = new ArrayList<String>();
        root.path("evaluationCriteria").forEach(item -> criteria.add(item.asString()));
        return new EvaluationContext(new GeneratedQuestion(root.path("content").asString(),
                root.path("sourceQuote").asString(), root.path("intent").asString(), criteria),
                initial.path("userAnswer").asString(), json.treeToValue(initial.path("evaluation"), Evaluation.class),
                initial.path("followUpQuestion").path("content").asString(), GroqLiveTest.FOLLOW_UP_ANSWER);
    }
}
