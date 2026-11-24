package com.example.shortener.common;

import java.util.List;

/** Standard error body: {"error": {"code","message","details","requestId"}}. */
public record ApiError(Body error) {
    public record Body(String code, String message, List<String> details, String requestId) {}

    public static ApiError of(String code, String message, List<String> details, String requestId) {
        return new ApiError(new Body(code, message, details == null ? List.of() : details, requestId));
    }
}
