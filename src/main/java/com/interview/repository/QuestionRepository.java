package com.interview.repository;

import com.interview.entity.Category;
import com.interview.entity.Question;
import com.interview.entity.Session;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface QuestionRepository extends JpaRepository<Question, Long> {
    Optional<Question> findByIdAndSession(Long id, Session session);
    List<Question> findByCategory(Category category);
    List<Question> findBySessionOrderByPositionAscIdAsc(Session session);
}
