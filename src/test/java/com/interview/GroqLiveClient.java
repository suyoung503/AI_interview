package com.interview;

import com.interview.dto.InterviewAiModels.Evaluation;
import com.interview.dto.InterviewAiModels.EvaluationContext;
import com.interview.dto.InterviewAiModels.GeneratedQuestion;
import com.interview.service.GroqService;

import java.net.URI;
import java.time.Duration;
import java.util.List;

final class GroqLiveClient extends GroqService {

    private static final int MAX_REQUESTS = 6;
    private final long intervalMillis;
    private int attempts;

    GroqLiveClient(String key) {
        super(key);
        intervalMillis = Duration.ofSeconds(65).toMillis();
    }

    GroqLiveClient(String key, URI loopback) {
        super(key, loopback, Duration.ofSeconds(1), Duration.ofSeconds(3));
        intervalMillis = 0;
    }

    @Override
    public List<GeneratedQuestion> generateQuestions(String resumeText) {
        reserveAttempt();
        return super.generateQuestions(resumeText);
    }

    @Override
    public Evaluation evaluate(EvaluationContext context) {
        reserveAttempt();
        return super.evaluate(context);
    }

    int attempts() {
        return attempts;
    }

    private synchronized void reserveAttempt() {
        if (attempts == MAX_REQUESTS) {
            throw new AssertionError("최대 Groq 요청 횟수(6회)에 도달했습니다.");
        }
        if (attempts > 0 && intervalMillis > 0) {
            try {
                Thread.sleep(intervalMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError("다음 요청을 보내기 전에 검증을 중단했습니다.");
            }
        }
        attempts++;
    }
}
