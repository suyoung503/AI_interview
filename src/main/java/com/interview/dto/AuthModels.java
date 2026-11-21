package com.interview.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

public final class AuthModels {

    private AuthModels() {}

    @Getter
    @NoArgsConstructor
    public static class Credentials {
        private String email;
        private String password;
    }

    @Getter
    @AllArgsConstructor
    public static class TokenResponse {
        private String token;
    }
}
