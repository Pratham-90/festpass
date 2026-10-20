package com.festpass.common;

import java.util.Map;
import org.springframework.http.ProblemDetail;

/** Builds RFC 7807 ProblemDetail bodies that always carry a stable {@code code}. */
public final class Problems {

    private Problems() {}

    public static ProblemDetail of(ErrorCode code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(code.status(), detail);
        problem.setProperty("code", code.name());
        return problem;
    }

    public static ProblemDetail of(ErrorCode code, String detail, Map<String, Object> properties) {
        ProblemDetail problem = of(code, detail);
        properties.forEach(problem::setProperty);
        return problem;
    }
}
