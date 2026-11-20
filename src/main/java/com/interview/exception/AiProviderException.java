package com.interview.exception;

import lombok.Getter;

@Getter
public final class AiProviderException extends CustomException {
    private final int upstreamStatus;

    public AiProviderException(int upstreamStatus) {
        super(ErrorCode.AI_BAD_RESPONSE);
        this.upstreamStatus = upstreamStatus;
    }
}
