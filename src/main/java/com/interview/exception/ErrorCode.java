package com.interview.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {
    // Auth
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 사용 중인 이메일입니다."),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다."),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 토큰입니다."),

    // Resource
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다."),
    SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "세션을 찾을 수 없습니다."),
    QUESTION_NOT_FOUND(HttpStatus.NOT_FOUND, "질문을 찾을 수 없습니다."),
    CATEGORY_NOT_FOUND(HttpStatus.NOT_FOUND, "카테고리를 찾을 수 없습니다."),
    RESUME_NOT_FOUND(HttpStatus.NOT_FOUND, "이력서를 찾을 수 없습니다."),

    // Business
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "요청 입력값이 올바르지 않습니다."),
    SESSION_ALREADY_DONE(HttpStatus.BAD_REQUEST, "이미 완료된 세션입니다."),
    UNAUTHORIZED_ACCESS(HttpStatus.FORBIDDEN, "접근 권한이 없습니다."),

    // Interview AI
    RESUME_INSUFFICIENT_FOR_INTERVIEW(HttpStatus.UNPROCESSABLE_CONTENT, "질문을 만들 수 있도록 이력서에 기술과 경험을 더 구체적으로 작성해 주세요."),
    AI_NOT_CONFIGURED(HttpStatus.SERVICE_UNAVAILABLE, "면접 AI 서비스가 설정되지 않았습니다."),
    AI_BAD_RESPONSE(HttpStatus.BAD_GATEWAY, "면접 AI의 정상 응답을 받지 못했습니다. 나중에 다시 시도해 주세요."),
    AI_RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "면접 AI 요청 한도에 도달했습니다. 잠시 후 다시 시도해 주세요."),
    AI_TIMEOUT(HttpStatus.GATEWAY_TIMEOUT, "면접 AI 응답 시간이 초과되었습니다. 나중에 다시 시도해 주세요.");

    private final HttpStatus status;
    private final String message;
}
