package com.interview.service;

import com.interview.dto.AnswerResponse;
import com.interview.dto.InterviewRequests.AnswerSubmitRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AnswerService {

    private final InterviewPersistenceService persistence;
    private final GroqService groq;

    public AnswerResponse submitAnswer(String email, AnswerSubmitRequest request) {
        var context = persistence.prepareEvaluation(email, request);
        var evaluation = groq.evaluate(context);
        return persistence.saveAnswer(email, request, evaluation, groq.getModel(), groq.getCriteriaVersion());
    }
}
