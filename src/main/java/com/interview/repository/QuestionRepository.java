package com.interview.repository;

import com.interview.entity.Category;
import com.interview.entity.Question;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface QuestionRepository extends JpaRepository<Question, Long> {
    List<Question> findByCategory(Category category);
}
