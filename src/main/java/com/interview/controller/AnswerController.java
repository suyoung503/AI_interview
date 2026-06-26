package com.interview.controller;

import com.interview.dto.AnswerResponse;
import com.interview.dto.AnswerSubmitRequest;
import com.interview.service.AnswerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/answers")
@RequiredArgsConstructor
public class AnswerController {

    private final AnswerService answerService;

    @PostMapping
    public ResponseEntity<AnswerResponse> submitAnswer(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody AnswerSubmitRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(answerService.submitAnswer(userDetails.getUsername(), request));
    }
}
