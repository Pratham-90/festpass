package com.festpass.event;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.festpass.IntegrationTest;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.ResultActions;

/** Helpers for building events through the real organizer API. */
public abstract class EventTestSupport extends IntegrationTest {

    protected Map<String, Object> eventBody(String title, String seatingMode) {
        Instant start = Instant.now().plus(Duration.ofDays(10)).truncatedTo(ChronoUnit.MINUTES);
        Map<String, Object> body = new HashMap<>();
        body.put("title", title);
        body.put("description", "A test event");
        body.put("category", "MUSIC");
        body.put("venueName", "Test Hall");
        body.put("startsAt", start.toString());
        body.put("endsAt", start.plus(Duration.ofHours(3)).toString());
        body.put("saleOpensAt", Instant.now().minus(Duration.ofDays(1)).toString());
        body.put("saleClosesAt", start.toString());
        body.put("seatingMode", seatingMode);
        return body;
    }

    protected ResultActions postJson(String url, Object body, MockHttpSession session) throws Exception {
        return mvc.perform(post(url).with(csrf()).session(session)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(body)));
    }

    protected JsonNode read(ResultActions actions) throws Exception {
        return objectMapper.readTree(actions.andReturn().getResponse().getContentAsString());
    }

    protected JsonNode createEvent(MockHttpSession organizer, String title, String seatingMode) throws Exception {
        return read(postJson("/api/organizer/events", eventBody(title, seatingMode), organizer)
                .andExpect(status().isCreated()));
    }

    protected long addSeatedTier(MockHttpSession organizer, long eventId, String name, long pricePaise) throws Exception {
        Map<String, Object> body = Map.of("name", name, "pricePaise", pricePaise);
        return read(postJson("/api/organizer/events/" + eventId + "/tiers", body, organizer)
                .andExpect(status().isCreated())).get("id").asLong();
    }

    protected long addGaTier(MockHttpSession organizer, long eventId, String name, long pricePaise, int capacity)
            throws Exception {
        Map<String, Object> body = Map.of("name", name, "pricePaise", pricePaise, "capacity", capacity);
        return read(postJson("/api/organizer/events/" + eventId + "/tiers", body, organizer)
                .andExpect(status().isCreated())).get("id").asLong();
    }

    protected ResultActions addSection(MockHttpSession organizer, long eventId, String name, long tierId,
                                       int rows, int seatsPerRow, List<Integer> aisleAfter) throws Exception {
        Map<String, Object> body = Map.of("name", name, "tierId", tierId, "rows", rows,
                "seatsPerRow", seatsPerRow, "aisleAfter", aisleAfter);
        return postJson("/api/organizer/events/" + eventId + "/sections", body, organizer);
    }

    protected ResultActions publish(MockHttpSession organizer, long eventId) throws Exception {
        return postJson("/api/organizer/events/" + eventId + "/publish", Map.of(), organizer);
    }

    /** A published seated event with one tier and one 2 x 5 section. Returns the event id. */
    protected long publishedSeatedEvent(MockHttpSession organizer, String title) throws Exception {
        long eventId = createEvent(organizer, title, "SEATED").get("id").asLong();
        long tierId = addSeatedTier(organizer, eventId, "Standard", 29_900);
        addSection(organizer, eventId, "Hall", tierId, 2, 5, List.of()).andExpect(status().isCreated());
        publish(organizer, eventId).andExpect(status().isOk());
        return eventId;
    }
}
