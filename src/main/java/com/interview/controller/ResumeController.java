package com.interview.controller;

import com.interview.dto.InterviewRequests.ResumeCreateRequest;
import com.interview.dto.ResumeResponse;
import com.interview.exception.CustomException;
import com.interview.exception.ErrorCode;
import com.interview.service.ResumeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/resumes")
@RequiredArgsConstructor
public class ResumeController {

    private final ResumeService resumeService;

    @PostMapping
    public ResponseEntity<ResumeResponse> create(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody(required = false) ResumeCreateRequest request) {
        if (request == null) {
            throw new CustomException(ErrorCode.INVALID_INPUT);
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(resumeService.create(userDetails.getUsername(), request));
    }

    @GetMapping
    public List<ResumeResponse> list(@AuthenticationPrincipal UserDetails userDetails) {
        return resumeService.list(userDetails.getUsername());
    }

    @GetMapping("/{id}")
    public ResumeResponse get(@AuthenticationPrincipal UserDetails userDetails, @PathVariable Long id) {
        return resumeService.get(userDetails.getUsername(), id);
    }
}
