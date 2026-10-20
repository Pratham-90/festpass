package com.festpass.common;

import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Turns every error into a ProblemDetail with a stable {@code code}.
 * Stack traces and internal messages never reach the client.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ProblemDetail> handleApi(ApiException e) {
        return ResponseEntity.status(e.code().status()).body(Problems.of(e.code(), e.getMessage(), e.properties()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException e) {
        return ResponseEntity.status(ErrorCode.FORBIDDEN.status())
                .body(Problems.of(ErrorCode.FORBIDDEN, "You are not allowed to do this"));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetail> handleIntegrity(DataIntegrityViolationException e) {
        log.warn("Data integrity violation: {}", e.getMostSpecificCause().getMessage());
        return ResponseEntity.status(ErrorCode.CONFLICT.status())
                .body(Problems.of(ErrorCode.CONFLICT, "The request conflicts with existing data"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception e) {
        log.error("Unexpected error", e);
        return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.status())
                .body(Problems.of(ErrorCode.INTERNAL_ERROR, "Something went wrong. Please try again."));
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException e, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError fe : e.getBindingResult().getFieldErrors()) {
            errors.putIfAbsent(fe.getField(), fe.getDefaultMessage());
        }
        e.getBindingResult().getGlobalErrors()
                .forEach(ge -> errors.putIfAbsent(ge.getObjectName(), ge.getDefaultMessage()));
        ProblemDetail body = Problems.of(ErrorCode.VALIDATION_FAILED, "Some fields are invalid", Map.of("errors", errors));
        return ResponseEntity.badRequest().body(body);
    }

    @Override
    protected ResponseEntity<Object> handleNoResourceFoundException(
            NoResourceFoundException e, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return ResponseEntity.status(status).body(Problems.of(ErrorCode.NOT_FOUND, "Not found"));
    }

    /** Adds a code to the ProblemDetails Spring creates itself (bad JSON, wrong method, ...). */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception e, Object body, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ResponseEntity<Object> response = super.handleExceptionInternal(e, body, headers, status, request);
        if (response != null && response.getBody() instanceof ProblemDetail problem
                && (problem.getProperties() == null || !problem.getProperties().containsKey("code"))) {
            problem.setProperty("code", codeFor(status).name());
        }
        return response;
    }

    private static ErrorCode codeFor(HttpStatusCode status) {
        return switch (status.value()) {
            case 400 -> ErrorCode.VALIDATION_FAILED;
            case 401 -> ErrorCode.UNAUTHENTICATED;
            case 403 -> ErrorCode.FORBIDDEN;
            case 404 -> ErrorCode.NOT_FOUND;
            case 409 -> ErrorCode.CONFLICT;
            default -> status.is5xxServerError() ? ErrorCode.INTERNAL_ERROR : ErrorCode.VALIDATION_FAILED;
        };
    }
}
