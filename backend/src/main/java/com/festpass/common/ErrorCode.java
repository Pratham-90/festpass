package com.festpass.common;

import org.springframework.http.HttpStatus;

/**
 * Stable error codes sent in the {@code code} field of every ProblemDetail response.
 * The frontend maps each code to a friendly message, so never rename one.
 */
public enum ErrorCode {
    // Booking (PLAN.md section 5.8)
    SEAT_UNAVAILABLE(HttpStatus.CONFLICT),
    SOLD_OUT(HttpStatus.CONFLICT),
    LIMIT_EXCEEDED(HttpStatus.CONFLICT),
    HOLD_EXPIRED(HttpStatus.CONFLICT),
    NOT_ADMITTED(HttpStatus.FORBIDDEN),
    SALE_NOT_OPEN(HttpStatus.CONFLICT),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS),
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST),

    // Auth and general
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED),
    BAD_CREDENTIALS(HttpStatus.UNAUTHORIZED),
    FORBIDDEN(HttpStatus.FORBIDDEN),
    CSRF_INVALID(HttpStatus.FORBIDDEN),
    EMAIL_TAKEN(HttpStatus.CONFLICT),
    NOT_FOUND(HttpStatus.NOT_FOUND),
    INVALID_STATE(HttpStatus.CONFLICT),
    CONFLICT(HttpStatus.CONFLICT),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
