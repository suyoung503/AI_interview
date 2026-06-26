package com.interview.service;

import com.interview.dto.AnswerResponse;
import com.interview.dto.AnswerSubmitRequest;
import com.interview.entity.Answer;
import com.interview.entity.Question;
import com.interview.entity.Session;
import com.interview.exception.CustomException;
import com.interview.exception.ErrorCode;
import com.interview.repository.AnswerRepository;
import com.interview.repository.QuestionRepository;
import com.interview.repository.SessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AnswerService {

    private final AnswerRepository answerRepository;
    private final SessionRepository sessionRepository;
    private final QuestionRepository questionRepository;
    private final ClaudeService claudeService;

    @Transactional
    public AnswerResponse submitAnswer(String email, AnswerSubmitRequest request) {
        Session session = sessionRepository.findById(request.getSessionId())
                .orElseThrow(() -> new CustomException(ErrorCode.SESSION_NOT_FOUND));

        if (!session.getUser().getEmail().equals(email)) {
            throw new CustomException(ErrorCode.UNAUTHORIZED_ACCESS);
        }
        if (session.getStatus() == Session.SessionStatus.DONE) {
            throw new CustomException(ErrorCode.SESSION_ALREADY_DONE);
        }

        Question question = questionRepository.findById(request.getQuestionId())
                .orElseThrow(() -> new CustomException(ErrorCode.QUESTION_NOT_FOUND));

        // Claude API 호출 → 피드백 + 점수 받기
        ClaudeService.FeedbackResult result =
                claudeService.getFeedback(question.getContent(), request.getUserAnswer());

        Answer answer = Answer.builder()
                .session(session)
                .question(question)
                .userAnswer(request.getUserAnswer())
                .feedback(result.feedback())
                .score(result.score())
                .build();
        answerRepository.save(answer);

        return new AnswerResponse(answer);
    }
}
