package com.interview.repository;

import com.interview.entity.Answer;
import com.interview.entity.Category;
import com.interview.entity.Question;
import com.interview.entity.Session;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface QuestionRepository extends JpaRepository<Question, Long> {
    Optional<Question> findByIdAndSession(Long id, Session session);
    List<Question> findByCategory(Category category);

    @Query("""
            select q as question, a as answer from Question q
            left join Answer a on a.question = q and a.session = :session
            where q.session = :session
            order by q.position, q.id, a.createdAt, a.id
            """)
    List<HistoryRow> findHistoryBySession(@Param("session") Session session);

    interface HistoryRow {
        Question getQuestion();
        Answer getAnswer();
    }
}
