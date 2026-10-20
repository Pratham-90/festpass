package com.festpass.auth;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.festpass.event.EventTestSupport;
import com.festpass.user.Role;
import com.festpass.user.User;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;

class RoleAccessIntegrationTest extends EventTestSupport {

    @Test
    void anonymousCanBrowseButNotManage() throws Exception {
        mvc.perform(get("/api/events")).andExpect(status().isOk());
        mvc.perform(get("/api/organizer/events"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mvc.perform(get("/api/admin/users")).andExpect(status().isUnauthorized());
    }

    @Test
    void studentCannotReachOrganizerAdminOrGateEndpoints() throws Exception {
        MockHttpSession student = loginAs(Role.STUDENT);
        mvc.perform(get("/api/organizer/events").session(student))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mvc.perform(get("/api/admin/users").session(student)).andExpect(status().isForbidden());
        mvc.perform(post("/api/checkin").with(csrf()).session(student)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void organizerCanManageEventsButNotUsers() throws Exception {
        MockHttpSession organizer = loginAs(Role.ORGANIZER);
        mvc.perform(get("/api/organizer/events").session(organizer)).andExpect(status().isOk());
        mvc.perform(get("/api/admin/users").session(organizer)).andExpect(status().isForbidden());
    }

    @Test
    void gateStaffCannotManageEvents() throws Exception {
        MockHttpSession gate = loginAs(Role.GATE_STAFF);
        mvc.perform(get("/api/organizer/events").session(gate)).andExpect(status().isForbidden());
    }

    @Test
    void organizerCannotTouchAnotherOrganizersEvent() throws Exception {
        MockHttpSession owner = loginAs(Role.ORGANIZER);
        MockHttpSession other = loginAs(Role.ORGANIZER);
        long eventId = createEvent(owner, "Owner's Night", "GENERAL").get("id").asLong();

        mvc.perform(get("/api/organizer/events/" + eventId).session(other))
                .andExpect(status().isForbidden());
        postJson("/api/organizer/events/" + eventId + "/tiers",
                Map.of("name", "Sneaky", "pricePaise", 100, "capacity", 10), other)
                .andExpect(status().isForbidden());
        publish(other, eventId).andExpect(status().isForbidden());

        // An admin can manage any event.
        MockHttpSession admin = loginAs(Role.ADMIN);
        mvc.perform(get("/api/organizer/events/" + eventId).session(admin)).andExpect(status().isOk());
    }

    @Test
    void adminCanListUsersAndChangeRoles() throws Exception {
        MockHttpSession admin = loginAs(Role.ADMIN);
        User student = createUser(Role.STUDENT);

        mvc.perform(get("/api/admin/users").param("q", student.getEmail()).session(admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].email").value(student.getEmail()));

        mvc.perform(put("/api/admin/users/" + student.getId() + "/role").with(csrf()).session(admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("role", "ORGANIZER"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ORGANIZER"));

        // The new role applies from the next login.
        MockHttpSession promoted = login(student.getEmail(), PASSWORD);
        mvc.perform(get("/api/organizer/events").session(promoted)).andExpect(status().isOk());
    }

    @Test
    void adminCannotChangeOwnRole() throws Exception {
        User admin = createUser(Role.ADMIN);
        MockHttpSession session = login(admin.getEmail(), PASSWORD);
        mvc.perform(put("/api/admin/users/" + admin.getId() + "/role").with(csrf()).session(session)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("role", "STUDENT"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));
    }
}
