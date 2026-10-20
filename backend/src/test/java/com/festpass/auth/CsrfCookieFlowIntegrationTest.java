package com.festpass.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.festpass.TestcontainersConfig;
import com.festpass.user.Role;
import com.festpass.user.User;
import com.festpass.user.UserService;
import jakarta.servlet.http.Cookie;
import java.util.Arrays;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * The real browser CSRF flow: read the XSRF-TOKEN cookie and send it back in the X-XSRF-TOKEN header.
 *
 * <p>Runs in its own Spring context on purpose. spring-security-test's {@code csrf()} helper, used by the
 * other tests, permanently swaps the CSRF filter's cookie repository for a test one in the context it
 * runs in, after which no XSRF-TOKEN cookie would be issued.
 */
@SpringBootTest(properties = "festpass.test.context=csrf-cookie-flow")
@AutoConfigureMockMvc
@Import(TestcontainersConfig.class)
class CsrfCookieFlowIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserService userService;

    @Test
    void spaCanLogInAndPostWithTheCookieToken() throws Exception {
        User user = userService.create("Cookie Tester", "cookie-flow@test.local", "password123", Role.STUDENT);

        MvcResult first = mvc.perform(get("/api/health")).andExpect(status().isOk()).andReturn();
        Cookie xsrf = lastCookie(first, "XSRF-TOKEN");
        assertThat(xsrf).isNotNull();
        assertThat(xsrf.isHttpOnly()).isFalse(); // JavaScript must be able to read it

        String login = objectMapper.writeValueAsString(Map.of("email", user.getEmail(), "password", "password123"));

        // A POST without the header is rejected...
        mvc.perform(post("/api/auth/login").cookie(xsrf).contentType(MediaType.APPLICATION_JSON).content(login))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"));

        // ...and accepted when the header echoes the cookie.
        MvcResult loggedIn = mvc.perform(post("/api/auth/login")
                        .cookie(xsrf)
                        .header("X-XSRF-TOKEN", xsrf.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(login))
                .andExpect(status().isOk())
                .andReturn();

        // Login rotates the token and sends the new one straight away, so the next POST works without a GET.
        Cookie rotated = lastCookie(loggedIn, "XSRF-TOKEN");
        assertThat(rotated).isNotNull();
        assertThat(rotated.getValue()).isNotBlank().isNotEqualTo(xsrf.getValue());

        MockHttpSession session = (MockHttpSession) loggedIn.getRequest().getSession(false);
        mvc.perform(post("/api/auth/logout").session(session)
                        .cookie(rotated)
                        .header("X-XSRF-TOKEN", rotated.getValue()))
                .andExpect(status().isNoContent());
    }

    /** A response may clear a cookie and then set it again; the browser keeps the last one. */
    private static Cookie lastCookie(MvcResult result, String name) {
        return Arrays.stream(result.getResponse().getCookies())
                .filter(c -> c.getName().equals(name))
                .reduce((first, second) -> second)
                .orElse(null);
    }
}
