package com.festpass.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.festpass.common.ErrorCode;
import com.festpass.common.Problems;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.stereotype.Component;

/** Writes security failures (401, 403, bad CSRF token) as JSON ProblemDetails instead of HTML or redirects. */
@Component
public class ProblemDetailSecurityHandlers implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public ProblemDetailSecurityHandlers(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException e)
            throws IOException {
        write(response, Problems.of(ErrorCode.UNAUTHENTICATED, "Please log in first"));
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException e)
            throws IOException {
        ProblemDetail problem = e instanceof CsrfException
                ? Problems.of(ErrorCode.CSRF_INVALID, "Your session token is missing or out of date. Refresh and try again.")
                : Problems.of(ErrorCode.FORBIDDEN, "You are not allowed to do this");
        write(response, problem);
    }

    private void write(HttpServletResponse response, ProblemDetail problem) throws IOException {
        response.setStatus(problem.getStatus());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), problem);
    }
}
