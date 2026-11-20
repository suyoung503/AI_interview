package com.interview.repository;

import com.interview.entity.Session;
import com.interview.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface SessionRepository extends JpaRepository<Session, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Session s where s.id = :id")
    Optional<Session> lockById(@Param("id") Long id);

    List<Session> findByUserOrderByCreatedAtDesc(User user);
}
