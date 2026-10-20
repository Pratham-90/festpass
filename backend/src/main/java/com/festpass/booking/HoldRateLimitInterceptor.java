package com.festpass.booking;

import com.festpass.auth.AppUserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Applies the per-user hold rate limit before the request body is even read, so every hold
 * request counts, including malformed ones. A 429 goes through the normal ProblemDetail handler.
 */
@Configuration
public class HoldRateLimitInterceptor implements HandlerInterceptor, WebMvcConfigurer {

    private final HoldRateLimiter rateLimiter;

    public HoldRateLimitInterceptor(HoldRateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(this).addPathPatterns("/api/events/*/holds");
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if ("POST".equals(request.getMethod()) && auth != null && auth.getPrincipal() instanceof AppUserPrincipal user) {
            rateLimiter.check(user.id());
        }
        return true;
    }
}
