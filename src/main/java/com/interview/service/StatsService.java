package com.interview.service;

import com.interview.dto.StatsResponse;
import com.interview.entity.Answer;
import com.interview.entity.Session;
import com.interview.entity.User;
import com.interview.exception.CustomException;
import com.interview.exception.ErrorCode;
import com.interview.repository.AnswerRepository;
import com.interview.repository.SessionRepository;
import com.interview.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StatsService {

    private final UserRepository userRepository;
    private final SessionRepository sessionRepository;
    private final AnswerRepository answerRepository;

    @Transactional(readOnly = true)
    public StatsResponse getMyStats(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));

        List<Session> sessions = sessionRepository.findByUserOrderByCreatedAtDesc(user);
        int totalSessions = sessions.size();
        int completedSessions = (int) sessions.stream()
                .filter(s -> s.getStatus() == Session.SessionStatus.DONE)
                .count();

        // 점수가 있는 답변만 통계에 포함
        List<Answer> answers = answerRepository.findBySession_User(user).stream()
                .filter(a -> a.getScore() != null)
                .toList();

        double overallAverage = answers.stream()
                .mapToInt(Answer::getScore)
                .average()
                .orElse(0.0);

        // 카테고리별로 답변을 묶어서 평균 계산
        Map<String, List<Answer>> byCategory = answers.stream()
                .collect(Collectors.groupingBy(a -> a.getQuestion().getCategory().getName()));

        List<StatsResponse.CategoryStat> categoryStats = byCategory.entrySet().stream()
                .map(entry -> new StatsResponse.CategoryStat(
                        entry.getKey(),
                        entry.getValue().stream().mapToInt(Answer::getScore).average().orElse(0.0),
                        entry.getValue().size()// 그룹의 답변 수
                ))
                .toList();

        return new StatsResponse(totalSessions, completedSessions, overallAverage, categoryStats);
    }
}
