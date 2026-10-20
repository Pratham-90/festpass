package com.festpass.common;

import java.util.Map;

/**
 * Thrown by services for expected failures (sold out, not found, ...).
 * The global handler turns it into a ProblemDetail with the error code.
 * {@code properties} are added to the response body, e.g. the IDs of seats that are no longer free.
 */
public class ApiException extends RuntimeException {

    private final ErrorCode code;
    private final Map<String, Object> properties;

    public ApiException(ErrorCode code, String detail) {
        this(code, detail, Map.of());
    }

    public ApiException(ErrorCode code, String detail, Map<String, Object> properties) {
        super(detail);
        this.code = code;
        this.properties = properties;
    }

    public ErrorCode code() {
        return code;
    }

    public Map<String, Object> properties() {
        return properties;
    }

    public static ApiException notFound(String what) {
        return new ApiException(ErrorCode.NOT_FOUND, what + " not found");
    }

    public static ApiException forbidden(String detail) {
        return new ApiException(ErrorCode.FORBIDDEN, detail);
    }

    public static ApiException invalidState(String detail) {
        return new ApiException(ErrorCode.INVALID_STATE, detail);
    }

    public static ApiException validation(String field, String message) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, message, Map.of("errors", Map.of(field, message)));
    }
}
