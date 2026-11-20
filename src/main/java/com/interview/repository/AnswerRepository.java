package com.interview.repository;

import com.interview.entity.Answer;
import com.interview.entity.Question;
import com.interview.entity.Session;
import com.interview.entity.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface AnswerRepository extends JpaRepository<Answer, Long> {
    List<Answer> findBySession(Session session);

    @EntityGraph(attributePaths = {"question", "question.category"})
    List<Answer> findBySessionOrderByCreatedAtAscIdAsc(Session session);

    boolean existsBySessionAndQuestion(Session session, Question question);
    Optional<Answer> findBySessionAndQuestion(Session session, Question question);
    List<Answer> findBySession_User(User user);  // 특정 유저의 모든 답변 조회
}
