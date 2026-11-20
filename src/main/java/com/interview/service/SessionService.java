package com.interview.service;

import com.interview.dto.SessionCreateRequest;
import com.interview.dto.SessionResponse;
import com.interview.entity.User;
import com.interview.exception.CustomException;
import com.interview.exception.ErrorCode;
import com.interview.repository.SessionRepository;
import com.interview.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SessionService {

    private final SessionRepository sessionRepository;
    private final UserRepository userRepository;
    private final ResumeService resumeService;
    private final GroqService groq;
    private final InterviewPersistenceService persistence;

    public SessionResponse createSession(String email, SessionCreateRequest request) {
        var resume = resumeService.get(email, request.getResumeId());
        var generated = groq.generateQuestions(resume.getText());
        return persistence.saveSession(email, resume.getId(), generated, groq.getModel(), groq.getCriteriaVersion());
    }

    @Transactional(readOnly = true)
    public List<SessionResponse> getMySessions(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        return sessionRepository.findByUserOrderByCreatedAtDesc(user)
                .stream()
                .map(SessionResponse::new)
                .toList();
        // 2. 각 Session 엔티티를 SessionResponse DTO로 변환
        //List<SessionResponse> result = new ArrayList<>();
        //for (Session session : sessions) {
        //    result.add(new SessionResponse(session));  // .map(SessionResponse::new) 가 이것
        //}
        //위 코드는 이걸 짧게 해서 stream map to List로 축약된거.
    }

    public SessionResponse endSession(String email, Long sessionId) {
        return persistence.endSession(email, sessionId);
    }
}
