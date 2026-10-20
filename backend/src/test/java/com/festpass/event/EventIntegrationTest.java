package com.festpass.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.festpass.user.Role;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;

class EventIntegrationTest extends EventTestSupport {

    @Test
    void createEventStartsAsDraftWithDefaultsAndSlug() throws Exception {
        MockHttpSession organizer = loginAs(Role.ORGANIZER);
        JsonNode event = createEvent(organizer, "Battle of the Bands 2026!", "SEATED");

        assertThat(event.get("status").asText()).isEqualTo("DRAFT");
        assertThat(event.get("slug").asText()).isEqualTo("battle-of-the-bands-2026");
        assertThat(event.get("holdMinutes").asInt()).isEqualTo(5);
        assertThat(event.get("maxTicketsPerUser").asInt()).isEqualTo(4);
        assertThat(event.get("flashDrop").asBoolean()).isFalse();
        assertThat(event.get("tiers")).isEmpty();

        // Same title again gets a different slug.
        JsonNode second = createEvent(organizer, "Battle of the Bands 2026!", "SEATED");
        assertThat(second.get("slug").asText()).isEqualTo("battle-of-the-bands-2026-2");
    }

    @Test
    void createEventValidatesFieldsAndTimes() throws Exception {
        MockHttpSession organizer = loginAs(Role.ORGANIZER);

        Map<String, Object> missing = eventBody("", "SEATED");
        missing.remove("category");
        postJson("/api/organizer/events", missing, organizer)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors.title").exists())
                .andExpect(jsonPath("$.errors.category").exists());

        Map<String, Object> backwards = eventBody("Backwards", "SEATED");
        backwards.put("endsAt", backwards.get("saleOpensAt"));
        postJson("/api/organizer/events", backwards, organizer)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.endsAt").exists());

        Map<String, Object> past = eventBody("Yesterday", "SEATED");
        past.put("startsAt", Instant.now().minusSeconds(86_400).toString());
        past.put("endsAt", Instant.now().minusSeconds(3_600).toString());
        past.put("saleClosesAt", Instant.now().minusSeconds(7_200).toString());
        postJson("/api/organizer/events", past, organizer)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.startsAt").exists());

        Map<String, Object> badEnum = eventBody("Bad enum", "SEATED");
        badEnum.put("category", "KARAOKE");
        postJson("/api/organizer/events", badEnum, organizer)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void draftIsHiddenUntilPublished() throws Exception {
        MockHttpSession organizer = loginAs(Role.ORGANIZER);
        String title = "Hidden Gig " + UUID.randomUUID().toString().substring(0, 6);
        JsonNode event = createEvent(organizer, title, "GENERAL");
        long id = event.get("id").asLong();
        String slug = event.get("slug").asText();

        mvc.perform(get("/api/events/" + slug)).andExpect(status().isNotFound());
        mvc.perform(get("/api/events/" + id + "/seatmap")).andExpect(status().isNotFound());
        mvc.perform(get("/api/events").param("q", title)).andExpect(jsonPath("$.length()").value(0));

        addGaTier(organizer, id, "General", 19_900, 300);
        publish(organizer, id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));

        mvc.perform(get("/api/events/" + slug))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value(title))
                .andExpect(jsonPath("$.tiers[0].remaining").value(300))
                .andExpect(jsonPath("$.accentColor").value("#ff3d81"));
        mvc.perform(get("/api/events").param("q", title.toLowerCase()))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].fromPricePaise").value(19_900));
    }

    @Test
    void listFiltersByCategory() throws Exception {
        MockHttpSession organizer = loginAs(Role.ORGANIZER);
        String marker = UUID.randomUUID().toString().substring(0, 8);
        Map<String, Object> tech = eventBody("Hackathon " + marker, "GENERAL");
        tech.put("category", "TECH");
        long techId = read(postJson("/api/organizer/events", tech, organizer).andExpect(status().isCreated()))
                .get("id").asLong();
        addGaTier(organizer, techId, "Entry", 0, 100);
        publish(organizer, techId).andExpect(status().isOk());

        mvc.perform(get("/api/events").param("q", marker).param("category", "TECH"))
                .andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/events").param("q", marker).param("category", "MUSIC"))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void publishNeedsTiersWithCapacity() throws Exception {
        MockHttpSession organizer = loginAs(Role.ORGANIZER);
        long id = createEvent(organizer, "Empty Show", "SEATED").get("id").asLong();

        publish(organizer, id)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));

        long tierId = addSeatedTier(organizer, id, "Gold", 49_900);
        publish(organizer, id).andExpect(status().isConflict()); // tier has no seats yet

        addSection(organizer, id, "Main", tierId, 2, 4, List.of()).andExpect(status().isCreated());
        publish(organizer, id).andExpect(status().isOk());
        publish(organizer, id).andExpect(status().isConflict()); // already published
    }

    @Test
    void layoutIsFrozenAfterPublishing() throws Exception {
        MockHttpSession organizer = loginAs(Role.ORGANIZER);
        long id = publishedSeatedEvent(organizer, "Frozen Layout");
        postJson("/api/organizer/events/" + id + "/tiers", Map.of("name", "Late", "pricePaise", 100), organizer)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));
    }

    @Test
    void tierRulesDependOnSeatingMode() throws Exception {
        MockHttpSession organizer = loginAs(Role.ORGANIZER);
        long seated = createEvent(organizer, "Seated Rules", "SEATED").get("id").asLong();
        long general = createEvent(organizer, "GA Rules", "GENERAL").get("id").asLong();

        postJson("/api/organizer/events/" + seated + "/tiers",
                Map.of("name", "VIP", "pricePaise", 100, "capacity", 50), organizer)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.capacity").exists());
        postJson("/api/organizer/events/" + general + "/tiers", Map.of("name", "GA", "pricePaise", 100), organizer)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.capacity").exists());

        addGaTier(organizer, general, "GA", 100, 10);
        postJson("/api/organizer/events/" + general + "/tiers",
                Map.of("name", "ga", "pricePaise", 100, "capacity", 10), organizer)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").exists());
    }

    @Test
    void updateChangesDetailsButNotSeatingModeOnceTiersExist() throws Exception {
        MockHttpSession organizer = loginAs(Role.ORGANIZER);
        long id = createEvent(organizer, "Before", "GENERAL").get("id").asLong();

        Map<String, Object> body = eventBody("After", "GENERAL");
        body.put("maxTicketsPerUser", 2);
        mvc.perform(put("/api/organizer/events/" + id).with(csrf()).session(organizer)
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("After"))
                .andExpect(jsonPath("$.maxTicketsPerUser").value(2));

        addGaTier(organizer, id, "GA", 100, 10);
        body.put("seatingMode", "SEATED");
        mvc.perform(put("/api/organizer/events/" + id).with(csrf()).session(organizer)
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isConflict());
    }

    @Test
    void organizerCanAssignGateStaff() throws Exception {
        MockHttpSession organizer = loginAs(Role.ORGANIZER);
        long id = createEvent(organizer, "Gate Test", "GENERAL").get("id").asLong();
        String gateEmail = createUser(Role.GATE_STAFF).getEmail();
        String studentEmail = createUser(Role.STUDENT).getEmail();

        postJson("/api/organizer/events/" + id + "/gate-staff", Map.of("email", gateEmail), organizer)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(gateEmail));
        // Assigning twice is harmless.
        postJson("/api/organizer/events/" + id + "/gate-staff", Map.of("email", gateEmail), organizer)
                .andExpect(status().isOk());
        postJson("/api/organizer/events/" + id + "/gate-staff", Map.of("email", studentEmail), organizer)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.email").exists());

        mvc.perform(get("/api/organizer/events/" + id + "/gate-staff").session(organizer))
                .andExpect(jsonPath("$.length()").value(1));
    }
}
