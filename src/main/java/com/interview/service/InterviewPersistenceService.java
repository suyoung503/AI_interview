package com.interview.service;

import com.interview.dto.AnswerResponse;
import com.interview.dto.AnswerSubmitRequest;
import com.interview.dto.InterviewAiModels.Evaluation;
import com.interview.dto.InterviewAiModels.EvaluationContext;
import com.interview.dto.InterviewAiModels.GeneratedQuestion;
import com.interview.dto.QuestionResponse;
import com.interview.dto.SessionResponse;
import com.interview.entity.Answer;
import com.interview.entity.Question;
import com.interview.entity.Resume;
import com.interview.entity.Session;
import com.interview.exception.CustomException;
import com.interview.exception.ErrorCode;
import com.interview.repository.AnswerRepository;
import com.interview.repository.QuestionRepository;
import com.interview.repository.ResumeRepository;
import com.interview.repository.SessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class InterviewPersistenceService {

    private final ResumeRepository resumes;
    private final SessionRepository sessions;
    private final QuestionRepository questions;
    private final AnswerRepository answers;

    @Transactional
    public SessionResponse saveSession(String email, Long resumeId, List<GeneratedQuestion> generated,
                                       String model, String criteriaVersion) {
        Resume resume = resumes.findByIdAndUserEmail(resumeId, email)
                .orElseThrow(() -> new CustomException(ErrorCode.RESUME_NOT_FOUND));
        Session session = sessions.save(Session.forResume(resume, model, criteriaVersion));
        List<QuestionResponse> roots = new ArrayList<>();
        for (var question : generated) {
            var root = questions.save(Question.root(session, roots.size() + 1, question));
            roots.add(new QuestionResponse(root));
        }
        return new SessionResponse(session, roots);
    }

    @Transactional(readOnly = true)
    public EvaluationContext prepareEvaluation(String email, AnswerSubmitRequest request) {
        Session session = sessions.findById(request.getSessionId())
                .orElseThrow(() -> new CustomException(ErrorCode.SESSION_NOT_FOUND));
        requireOwner(session, email);
        Question question = unansweredQuestion(session, request.getQuestionId());
        Question parent = question.getParentQuestion();
        if (parent == null) {
            return new EvaluationContext(generated(question), request.getUserAnswer(), null, null, null);
        }

        Answer initial = answers.findBySessionAndQuestion(session, parent).orElseThrow();
        return new EvaluationContext(generated(parent), initial.getUserAnswer(), initial.getEvaluation(),
                question.getContent(), request.getUserAnswer());
    }

    @Transactional
    public AnswerResponse saveAnswer(String email, AnswerSubmitRequest request, Evaluation evaluation,
                                     String model, String criteriaVersion) {
        Session session = lockOwnedSession(email, request.getSessionId());
        Question question = unansweredQuestion(session, request.getQuestionId());
        Answer answer = answers.save(Answer.evaluated(question, request.getUserAnswer(), evaluation, model, criteriaVersion));
        Question followUp = evaluation.followUpQuestion() == null ? null :
                questions.save(Question.followUp(session, question, evaluation.followUpQuestion()));
        return new AnswerResponse(answer, followUp);
    }

    @Transactional
    public SessionResponse endSession(String email, Long sessionId) {
        Session session = lockOwnedSession(email, sessionId);
        requireInProgress(session);
        session.done();
        return new SessionResponse(session);
    }

    // 답변 저장과 완료 모두 세션 행부터 잠근 뒤 최신 상태를 확인한다.
    private Session lockOwnedSession(String email, Long id) {
        Session session = sessions.lockById(id)
                .orElseThrow(() -> new CustomException(ErrorCode.SESSION_NOT_FOUND));
        requireOwner(session, email);
        return session;
    }

    private Question unansweredQuestion(Session session, Long id) {
        Question question = questions.findByIdAndSession(id, session)
                .orElseThrow(() -> new CustomException(ErrorCode.QUESTION_NOT_FOUND));
        requireInProgress(session);
        if (answers.existsBySessionAndQuestion(session, question)) {
            throw new CustomException(ErrorCode.ANSWER_ALREADY_EXISTS);
        }
        return question;
    }

    private static void requireOwner(Session session, String email) {
        if (!session.getUser().getEmail().equals(email)) {
            throw new CustomException(ErrorCode.SESSION_NOT_FOUND);
        }
    }

    private static void requireInProgress(Session session) {
        if (session.getStatus() == Session.SessionStatus.DONE) {
            throw new CustomException(ErrorCode.SESSION_ALREADY_DONE);
        }
    }

    private static GeneratedQuestion generated(Question root) {
        return new GeneratedQuestion(root.getContent(), root.getSourceQuote(), root.getIntent(), root.getEvaluationCriteria());
    }
}
