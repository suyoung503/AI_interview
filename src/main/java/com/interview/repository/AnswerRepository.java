package com.interview.repository;

import com.interview.entity.Answer;
import com.interview.entity.Session;
import com.interview.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface AnswerRepository extends JpaRepository<Answer, Long> {
    List<Answer> findBySession(Session session);
    List<Answer> findBySession_User(User user);  // 특정 유저의 모든 답변 조회
}
