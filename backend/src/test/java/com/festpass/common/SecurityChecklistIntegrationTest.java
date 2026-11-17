package com.festpass.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.festpass.IntegrationTest;
import com.festpass.user.Role;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;

/** The Phase 7 security checklist items that are not already covered by the auth and role tests. */
class SecurityChecklistIntegrationTest extends IntegrationTest {

    @Autowired JdbcTemplate jdbc;
    @Autowired GlobalExceptionHandler handler;

    @Test
    void passwordsAreStoredAsBcryptHashes() throws Exception {
        String email = uniqueEmail("hash");
        mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Hash Check", "email", email, "password", PASSWORD))))
                .andExpect(status().isCreated());

        String stored = jdbc.queryForObject("SELECT password_hash FROM users WHERE email = ?", String.class, email);
        assertThat(stored).doesNotContain(PASSWORD).startsWith("$2");
    }

    @Test
    void badRequestsGetAProblemWithoutInternals() throws Exception {
        MockHttpSession student = loginAs(Role.STUDENT);
        String body = mvc.perform(post("/api/orders").with(csrf()).session(student)
                        .header("Idempotency-Key", "k-" + System.nanoTime())
                        .contentType(MediaType.APPLICATION_JSON).content("{ this is not json"))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("Exception").doesNotContain("at com.").doesNotContain("trace");
    }

    @Test
    void unexpectedErrorsHideTheirDetails() {
        ResponseEntity<ProblemDetail> response =
                handler.handleUnexpected(new IllegalStateException("internal detail xyz-42"));
        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getProperties()).containsEntry("code", "INTERNAL_ERROR");
        assertThat(String.valueOf(response.getBody().getDetail())).doesNotContain("xyz-42");
        assertThat(String.valueOf(response.getBody().getProperties())).doesNotContain("xyz-42");
    }
}
