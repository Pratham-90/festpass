package com.festpass;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.festpass.user.Role;
import com.festpass.user.User;
import com.festpass.user.UserService;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Base class for integration tests: full Spring context, MockMvc, a real PostgreSQL and Mailpit
 * (Testcontainers), and a {@link MutableClock}. Background jobs are off so tests run them explicitly,
 * and the mock payment gateway answers instantly.
 * All subclasses share one context and one database, so tests use unique data instead of cleaning up.
 */
@SpringBootTest(properties = {
        "festpass.scheduling.enabled=false",
        "festpass.payment.mock-latency-min=0ms",
        "festpass.payment.mock-latency-max=0ms"
})
@AutoConfigureMockMvc
@Import({TestcontainersConfig.class, IntegrationTest.TestClockConfig.class})
public abstract class IntegrationTest {

    protected static final String PASSWORD = "password123";

    @TestConfiguration(proxyBeanMethods = false)
    static class TestClockConfig {
        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock();
        }
    }

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected UserService userService;

    @Autowired
    protected MutableClock clock;

    @Autowired
    private Clock injectedClock;

    @BeforeEach
    void resetClock() {
        if (injectedClock != clock) {
            throw new IllegalStateException("The application is not using the test clock");
        }
        clock.reset();
    }

    protected static String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8) + "@test.local";
    }

    protected String json(Object body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    protected User createUser(Role role) {
        return userService.create("Test " + role, uniqueEmail(role.name().toLowerCase()), PASSWORD, role);
    }

    /** Creates a user with the role and logs in through the real endpoint. */
    protected MockHttpSession loginAs(Role role) throws Exception {
        return login(createUser(role).getEmail(), PASSWORD);
    }

    protected MockHttpSession login(String email, String password) throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "password", password))))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }
}
