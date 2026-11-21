package com.interview.service;

import com.interview.dto.InterviewRequests.ResumeCreateRequest;
import com.interview.dto.ResumeResponse;
import com.interview.entity.Resume;
import com.interview.entity.User;
import com.interview.exception.CustomException;
import com.interview.exception.ErrorCode;
import com.interview.repository.ResumeRepository;
import com.interview.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ResumeService {

    private final ResumeRepository resumeRepository;
    private final UserRepository userRepository;

    @Transactional
    public ResumeResponse create(String email, ResumeCreateRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
        Resume resume = Resume.builder()
                .user(user)
                .text(request.getText())
                .build();
        return new ResumeResponse(resumeRepository.save(resume));
    }

    @Transactional(readOnly = true)
    public List<ResumeResponse> list(String email) {
        return resumeRepository.findMetadataByUserEmail(email);
    }

    @Transactional(readOnly = true)
    public ResumeResponse get(String email, Long id) {
        return resumeRepository.findByIdAndUserEmail(id, email)
                .map(ResumeResponse::new)
                .orElseThrow(() -> new CustomException(ErrorCode.RESUME_NOT_FOUND));
    }
}
