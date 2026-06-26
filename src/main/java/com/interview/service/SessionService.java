package com.interview.service;

import com.interview.dto.QuestionResponse;
import com.interview.dto.SessionCreateRequest;
import com.interview.dto.SessionResponse;
import com.interview.entity.Category;
import com.interview.entity.Session;
import com.interview.entity.User;
import com.interview.exception.CustomException;
import com.interview.exception.ErrorCode;
import com.interview.repository.CategoryRepository;
import com.interview.repository.QuestionRepository;
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
    private final CategoryRepository categoryRepository;
    private final QuestionRepository questionRepository;

    @Transactional
    public SessionResponse createSession(String email, SessionCreateRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new CustomException(ErrorCode.CATEGORY_NOT_FOUND));

        Session session = Session.builder()
                .user(user)
                .category(category)
                .build();
        sessionRepository.save(session);

        List<QuestionResponse> questions = questionRepository.findByCategory(category)
                .stream()
                .map(QuestionResponse::new)
                .toList();

        return new SessionResponse(session, questions);
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

    @Transactional
    public SessionResponse endSession(String email, Long sessionId) {
        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new CustomException(ErrorCode.SESSION_NOT_FOUND));

        if (!session.getUser().getEmail().equals(email)) {
            throw new CustomException(ErrorCode.UNAUTHORIZED_ACCESS);
        }
        if (session.getStatus() == Session.SessionStatus.DONE) {
            throw new CustomException(ErrorCode.SESSION_ALREADY_DONE);
        }

        session.done();
        return new SessionResponse(session);
    }
}
