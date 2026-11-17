package com.festpass;

import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * MockMvc's csrf() helper swaps the CSRF filter's cookie repository for a session-based test one
 * (see CsrfCookieFlowIntegrationTest). Tests that then make real HTTP requests (WebSocket logins,
 * Stress Arena bots) put the real one back first.
 */
public final class RealHttpCsrf {

    private RealHttpCsrf() {}

    public static void restore(FilterChainProxy securityFilters, CsrfTokenRepository realRepository) {
        securityFilters.getFilterChains().forEach(chain -> chain.getFilters().stream()
                .filter(CsrfFilter.class::isInstance)
                .forEach(filter -> ReflectionTestUtils.setField(filter, "tokenRepository", realRepository)));
    }
}
