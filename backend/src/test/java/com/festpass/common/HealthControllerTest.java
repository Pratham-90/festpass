package com.festpass.common;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.festpass.IntegrationTest;
import org.junit.jupiter.api.Test;

class HealthControllerTest extends IntegrationTest {

    @Test
    void healthReportsUpWithDatabase() throws Exception {
        mvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.database").value("UP"))
                .andExpect(jsonPath("$.time").exists());
    }

    @Test
    void unknownApiPathIsNotFoundProblem() throws Exception {
        mvc.perform(get("/api/does-not-exist"))
                .andExpect(status().isUnauthorized()); // unknown /api paths need a login first

        mvc.perform(get("/api/events/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }
}
