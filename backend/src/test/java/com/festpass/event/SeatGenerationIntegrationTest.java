package com.festpass.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.festpass.user.Role;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

class SeatGenerationIntegrationTest extends EventTestSupport {

    @Test
    void sectionGeneratesSeatsWithUniqueLabelsAndAisles() throws Exception {
        MockHttpSession organizer = loginAs(Role.ORGANIZER);
        long eventId = createEvent(organizer, "Seat Gen", "SEATED").get("id").asLong();
        long tierId = addSeatedTier(organizer, eventId, "Gold", 49_900);

        addSection(organizer, eventId, "Stalls", tierId, 3, 10, List.of(3, 7))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.seatCount").value(30));

        JsonNode map = read(mvc.perform(get("/api/organizer/events/" + eventId + "/seatmap").session(organizer))
                .andExpect(status().isOk()));
        List<JsonNode> seats = list(map.get("seats"));

        // Correct count and every label unique.
        assertThat(seats).hasSize(30);
        Set<String> labels = new HashSet<>();
        seats.forEach(s -> labels.add(s.get("section").asText() + "-" + s.get("row").asText() + s.get("number").asInt()));
        assertThat(labels).hasSize(30);

        // Rows A, B, C with seats 1..10, all available and coloured by tier.
        assertThat(seats.stream().map(s -> s.get("row").asText()).distinct()).containsExactly("A", "B", "C");
        assertThat(seats.stream().filter(s -> s.get("row").asText().equals("A")).map(s -> s.get("number").asInt()))
                .containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
        assertThat(seats).allSatisfy(s -> {
            assertThat(s.get("status").asText()).isEqualTo("AVAILABLE");
            assertThat(s.get("color").asText()).isEqualTo("#ff3d81");
        });

        // Aisles: the gap after seats 3 and 7 is wider than a normal gap.
        List<Integer> xs = seats.stream().filter(s -> s.get("row").asText().equals("A"))
                .map(s -> s.get("x").asInt()).toList();
        int normalGap = xs.get(1) - xs.get(0);
        assertThat(xs.get(3) - xs.get(2)).isEqualTo(normalGap + SeatLayoutGenerator.AISLE_WIDTH);
        assertThat(xs.get(7) - xs.get(6)).isEqualTo(normalGap + SeatLayoutGenerator.AISLE_WIDTH);
        assertThat(xs.get(5) - xs.get(4)).isEqualTo(normalGap);

        // The tier's capacity is now the number of seats.
        assertThat(map.get("tiers").get(0).get("capacity").asInt()).isEqualTo(30);
        assertThat(map.get("tiers").get(0).get("remaining").asInt()).isEqualTo(30);
    }

    @Test
    void secondSectionGoesBelowTheFirst() throws Exception {
        MockHttpSession organizer = loginAs(Role.ORGANIZER);
        long eventId = createEvent(organizer, "Two Sections", "SEATED").get("id").asLong();
        long vip = addSeatedTier(organizer, eventId, "VIP", 79_900);
        long silver = addSeatedTier(organizer, eventId, "Silver", 24_900);

        JsonNode front = read(addSection(organizer, eventId, "Front", vip, 2, 8, List.of(4))
                .andExpect(status().isCreated()));
        JsonNode back = read(addSection(organizer, eventId, "Back", silver, 3, 12, List.of())
                .andExpect(status().isCreated()));

        assertThat(back.get("minY").asInt()).isGreaterThan(front.get("maxY").asInt());

        JsonNode map = read(mvc.perform(get("/api/organizer/events/" + eventId + "/seatmap").session(organizer)));
        assertThat(map.get("seats")).hasSize(16 + 36);
        assertThat(list(map.get("sections")).stream().map(s -> s.get("name").asText())).containsExactly("Front", "Back");
        assertThat(map.get("bounds").get("maxY").asInt()).isEqualTo(back.get("maxY").asInt());
        // Each tier keeps its own colour on its seats.
        assertThat(list(map.get("seats")).stream().map(s -> s.get("color").asText()).distinct())
                .containsExactlyInAnyOrder("#ff3d81", "#7c5cff");
    }

    @Test
    void sectionRequestsAreValidated() throws Exception {
        MockHttpSession organizer = loginAs(Role.ORGANIZER);
        long eventId = createEvent(organizer, "Bad Sections", "SEATED").get("id").asLong();
        long tierId = addSeatedTier(organizer, eventId, "Gold", 100);
        long otherEvent = createEvent(organizer, "Other", "SEATED").get("id").asLong();
        long otherTier = addSeatedTier(organizer, otherEvent, "Gold", 100);

        addSection(organizer, eventId, "A", tierId, 2, 10, List.of(10))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.aisleAfter").exists());
        addSection(organizer, eventId, "A", tierId, 0, 10, List.of())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.rows").exists());
        addSection(organizer, eventId, "A", otherTier, 2, 10, List.of())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.tierId").exists());

        addSection(organizer, eventId, "Main", tierId, 1, 5, List.of()).andExpect(status().isCreated());
        addSection(organizer, eventId, "main", tierId, 1, 5, List.of())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").exists());
    }

    @Test
    void generalAdmissionEventsHaveNoSections() throws Exception {
        MockHttpSession organizer = loginAs(Role.ORGANIZER);
        long eventId = createEvent(organizer, "GA Only", "GENERAL").get("id").asLong();
        long tierId = addGaTier(organizer, eventId, "GA", 100, 50);
        addSection(organizer, eventId, "Nope", tierId, 2, 5, List.of())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));
    }

    @Test
    void publicSeatMapReturnsTheGeneratedLayout() throws Exception {
        MockHttpSession organizer = loginAs(Role.ORGANIZER);
        long eventId = publishedSeatedEvent(organizer, "Public Map");

        mvc.perform(get("/api/events/" + eventId + "/seatmap"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.seatingMode").value("SEATED"))
                .andExpect(jsonPath("$.seats.length()").value(10))
                .andExpect(jsonPath("$.sections[0].name").value("Hall"))
                .andExpect(jsonPath("$.sections[0].seatCount").value(10))
                .andExpect(jsonPath("$.tiers[0].remaining").value(10))
                .andExpect(jsonPath("$.bounds").exists());
    }

    private static List<JsonNode> list(JsonNode array) {
        return new ArrayList<>(StreamSupport.stream(array.spliterator(), false).toList());
    }
}
