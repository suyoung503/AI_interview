package com.interview.controller;

import com.interview.dto.SessionCreateRequest;
import com.interview.dto.SessionResponse;
import com.interview.service.SessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/sessions")
@RequiredArgsConstructor
public class SessionController {

    private final SessionService sessionService;

    @PostMapping
    public ResponseEntity<SessionResponse> createSession(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestBody SessionCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(sessionService.createSession(userDetails.getUsername(), request));
    }

    @GetMapping
    public ResponseEntity<List<SessionResponse>> getMySessions(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(sessionService.getMySessions(userDetails.getUsername()));
    }

    @PatchMapping("/{id}/done")
    public ResponseEntity<SessionResponse> endSession(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id) {
        return ResponseEntity.ok(sessionService.endSession(userDetails.getUsername(), id));
    }
}
