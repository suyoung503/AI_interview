package com.interview.repository;

import com.interview.dto.ResumeResponse;
import com.interview.entity.Resume;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ResumeRepository extends JpaRepository<Resume, Long> {

    Optional<Resume> findByIdAndUserEmail(Long id, String email);

    @Query("""
            select new com.interview.dto.ResumeResponse(r.id, r.createdAt)
            from Resume r
            where r.user.email = :email
            order by r.createdAt desc, r.id desc
            """)
    List<ResumeResponse> findMetadataByUserEmail(@Param("email") String email);
}
