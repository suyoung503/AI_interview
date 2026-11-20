package com.interview.exception;

import lombok.Builder;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @Builder
    record ErrorResponse(int status, String message) {}

    @ExceptionHandler(CustomException.class)
    public ResponseEntity<ErrorResponse> handleCustomException(CustomException e) {
        return errorResponse(e.getErrorCode());
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class,
            HandlerMethodValidationException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ErrorResponse> handleValidationException() {
        return errorResponse(ErrorCode.INVALID_INPUT);
    }

    private ResponseEntity<ErrorResponse> errorResponse(ErrorCode code) {
        return ResponseEntity
                .status(code.getStatus())
                .body(new ErrorResponse(code.getStatus().value(), code.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception e) {
        return ResponseEntity
                .internalServerError()
                .body(new ErrorResponse(500, "서버 내부 오류가 발생했습니다."));
    }
}
