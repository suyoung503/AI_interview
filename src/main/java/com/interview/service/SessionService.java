package com.interview.service;

import com.interview.dto.AnswerResponse;
import com.interview.dto.QuestionResponse;
import com.interview.dto.ResumeResponse;
import com.interview.dto.SessionCreateRequest;
import com.interview.dto.SessionResponse;
import com.interview.entity.Answer;
import com.interview.entity.Question;
import com.interview.entity.User;
import com.interview.exception.CustomException;
import com.interview.exception.ErrorCode;
import com.interview.repository.AnswerRepository;
import com.interview.repository.QuestionRepository;
import com.interview.repository.SessionRepository;
import com.interview.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SessionService {

    private final SessionRepository sessionRepository;
    private final UserRepository userRepository;
    private final AnswerRepository answerRepository;
    private final QuestionRepository questionRepository;
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

    @Transactional(readOnly = true)
    public SessionResponse getSession(String email, Long id) {
        var session = sessionRepository.findByIdAndUserEmail(id, email)
                .orElseThrow(() -> new CustomException(ErrorCode.SESSION_NOT_FOUND));

        Map<Long, List<Answer>> answersByQuestion = answerRepository.findBySessionOrderByCreatedAtAscIdAsc(session)
                .stream()
                .collect(Collectors.groupingBy(a -> a.getQuestion().getId(), LinkedHashMap::new, Collectors.toList()));
        // 구형 고정 질문은 세션이 아닌 과거 답변을 통해서만 연결된다.
        List<Question> questions = session.getResume() == null
                ? answersByQuestion.values().stream().map(group -> group.get(0).getQuestion()).toList()
                : questionRepository.findBySessionOrderByPositionAscIdAsc(session);
        Map<Long, Question> followUpsByParent = questions.stream()
                .filter(question -> question.getParentQuestion() != null)
                .collect(Collectors.toMap(question -> question.getParentQuestion().getId(), Function.identity()));

        List<QuestionResponse> history = questions.stream().map(question -> {
            var followUp = followUpsByParent.get(question.getId());
            var answers = answersByQuestion.getOrDefault(question.getId(), List.of()).stream()
                    .map(answer -> new AnswerResponse(answer, followUp))
                    .toList();
            return new QuestionResponse(question, answers);
        }).toList();
        var resume = session.getResume() == null ? null : new ResumeResponse(session.getResume());
        return new SessionResponse(session, history, resume);
    }

    public SessionResponse endSession(String email, Long sessionId) {
        return persistence.endSession(email, sessionId);
    }
}
