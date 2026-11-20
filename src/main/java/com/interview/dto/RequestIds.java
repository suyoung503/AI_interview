package com.interview.dto;

import tools.jackson.databind.JsonNode;

final class RequestIds {

    private RequestIds() {}

    // JSON 숫자 1.5나 문자열 "1"이 실제 ID로 강제 변환되지 않게 한다.
    static Long integer(JsonNode value) {
        return value != null && value.isIntegralNumber() && value.canConvertToLong()
                ? value.longValue() : null;
    }
}
