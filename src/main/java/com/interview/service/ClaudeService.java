package com.interview.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;

@Service
public class ClaudeService {

    private final RestClient restClient;
    private final String model;

    public ClaudeService(
            @Value("${claude.api-key}") String apiKey,
            @Value("${claude.api-url}") String apiUrl,
            @Value("${claude.model}") String model) {
        this.model = model;
        this.restClient = RestClient.builder()
                .baseUrl(apiUrl)
                .defaultHeader("x-api-key", apiKey)
                .defaultHeader("anthropic-version", "2023-06-01")
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    public FeedbackResult getFeedback(String question, String userAnswer) {
        String prompt = """
                당신은 IT 기술 면접 평가자입니다.

                면접 질문: %s

                지원자 답변: %s

                위 답변을 평가해주세요. 반드시 아래 형식으로만 응답하세요.
                점수: [1~10 사이 숫자만]
                피드백: [구체적인 피드백]
                """.formatted(question, userAnswer);

        ClaudeRequest request = new ClaudeRequest(
                model, 1024, List.of(new Message("user", prompt)));

        ClaudeResponse response = restClient.post()
                .body(request)
                .retrieve()
                .body(ClaudeResponse.class);

        String text = response.content().get(0).text();
        return parseResponse(text);
    }

    private FeedbackResult parseResponse(String text) {
        int score = 5;
        String feedback = text;

        try {
            StringBuilder feedbackBuilder = new StringBuilder();
            boolean feedbackStarted = false;

            for (String line : text.split("\n")) {
                if (line.startsWith("점수:")) {
                    score = Integer.parseInt(line.replace("점수:", "").trim().replaceAll("[^0-9]", ""));
                } else if (line.startsWith("피드백:")) {
                    feedbackStarted = true;
                    feedbackBuilder.append(line.replace("피드백:", "").trim());
                } else if (feedbackStarted && !line.isBlank()) {
                    feedbackBuilder.append(" ").append(line.trim());
                }
            }

            if (!feedbackBuilder.isEmpty()) {
                feedback = feedbackBuilder.toString().trim();
            }
        } catch (Exception ignored) {}

        return new FeedbackResult(score, feedback);
    }

    // Claude API 요청/응답 형태 정의
    record ClaudeRequest(
            String model,
            @JsonProperty("max_tokens") int maxTokens,
            List<Message> messages) {}

    record Message(String role, String content) {}

    record ClaudeResponse(List<Content> content) {}

    record Content(String type, String text) {}

    public record FeedbackResult(int score, String feedback) {}
}
