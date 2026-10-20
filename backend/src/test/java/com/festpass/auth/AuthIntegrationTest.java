package com.festpass.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.festpass.IntegrationTest;
import com.festpass.user.Role;
import com.festpass.user.User;
import com.festpass.user.UserRepository;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

class AuthIntegrationTest extends IntegrationTest {

    @Autowired
    UserRepository users;

    @Test
    void registerCreatesStudentAndLogsIn() throws Exception {
        String email = uniqueEmail("Reg");
        MvcResult result = mvc.perform(post("/api/auth/register").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Asha", "email", email, "password", PASSWORD))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(email.toLowerCase()))
                .andExpect(jsonPath("$.role").value("STUDENT"))
                .andReturn();

        MockHttpSession session = (MockHttpSession) result.getRequest().getSession(false);
        mvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Asha"));

        User saved = users.findByEmail(email.toLowerCase()).orElseThrow();
        assertThat(saved.getPasswordHash()).startsWith("$2").isNotEqualTo(PASSWORD);
    }

    @Test
    void registerRejectsDuplicateEmailIgnoringCase() throws Exception {
        String email = uniqueEmail("dup");
        Map<String, String> body = Map.of("name", "One", "email", email, "password", PASSWORD);
        mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isCreated());

        Map<String, String> again = Map.of("name", "Two", "email", email.toUpperCase(), "password", PASSWORD);
        mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json(again)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_TAKEN"));
    }

    @Test
    void registerValidatesFields() throws Exception {
        mvc.perform(post("/api/auth/register").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "", "email", "not-an-email", "password", "short"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors.name").exists())
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.password").exists());
    }

    @Test
    void loginWithWrongPasswordIsRejected() throws Exception {
        User user = createUser(Role.STUDENT);
        mvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", user.getEmail(), "password", "wrong-password"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("BAD_CREDENTIALS"));

        mvc.perform(post("/api/auth/login").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "nobody@test.local", "password", PASSWORD))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("BAD_CREDENTIALS"));
    }

    @Test
    void meWithoutLoginIsUnauthenticated() throws Exception {
        mvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void logoutEndsTheSession() throws Exception {
        MockHttpSession session = loginAs(Role.STUDENT);
        mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isOk());

        mvc.perform(post("/api/auth/logout").with(csrf()).session(session))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isUnauthorized());
    }

    @Test
    void postWithoutCsrfTokenIsRejected() throws Exception {
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "a@b.c", "password", PASSWORD))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"));
    }
}
