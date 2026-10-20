package com.festpass.checkin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.festpass.auth.AppUserPrincipal;
import com.festpass.booking.BookingTestSupport;
import com.festpass.user.Role;
import com.festpass.user.User;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.ResultActions;

class CheckinIntegrationTest extends BookingTestSupport {

    MockHttpSession organizer;
    long eventId;
    AppUserPrincipal gate;
    String code;

    @BeforeEach
    void ticketAndGateStaff() throws Exception {
        organizer = loginAs(Role.ORGANIZER);
        eventId = publishedSeatedEvent(organizer, "Gate Night");
        User staff = createUser(Role.GATE_STAFF);
        postJson("/api/organizer/events/" + eventId + "/gate-staff", Map.of("email", staff.getEmail()), organizer)
                .andExpect(status().isOk());
        gate = new AppUserPrincipal(users.findById(staff.getId()).orElseThrow());

        AppUserPrincipal buyer = student();
        JsonNode order = buySeats(eventId, buyer, List.of(seatIds(eventId).get(0)));
        String ticketId = order.get("ticketIds").get(0).asText();
        code = read(call(get("/api/tickets/" + ticketId), buyer, null)).get("code").asText();
    }

    private ResultActions scan(AppUserPrincipal staff, long event, String scannedCode) throws Exception {
        return call(post("/api/checkin"), staff, Map.of("eventId", event, "code", scannedCode));
    }

    @Test
    void firstScanIsValidAndTheSecondIsAlreadyUsed() throws Exception {
        scan(gate, eventId, code)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("VALID"))
                .andExpect(jsonPath("$.seatLabel").value("Hall · Row A · Seat 1"));

        scan(gate, eventId, code)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("ALREADY_USED"))
                .andExpect(jsonPath("$.checkedInAt").exists())
                .andExpect(jsonPath("$.checkedInBy").value(gate.name()));
    }

    @Test
    void tamperedOrMadeUpCodesAreInvalid() throws Exception {
        String[] parts = code.split("\\.");
        String otherTicket = UUID.randomUUID() + "." + parts[1] + "." + parts[2];   // valid signature, other ticket
        String otherEvent = parts[0] + "." + (eventId + 1) + "." + parts[2];        // moved to another event
        String badSignature = parts[0] + "." + parts[1] + "." + "A".repeat(parts[2].length());

        for (String bad : List.of(otherTicket, otherEvent, badSignature, "hello", "")) {
            if (bad.isEmpty()) {
                scan(gate, eventId, bad).andExpect(status().isBadRequest());
            } else {
                scan(gate, eventId, bad).andExpect(jsonPath("$.result").value("INVALID"));
            }
        }
        // None of that checked the real ticket in.
        scan(gate, eventId, code).andExpect(jsonPath("$.result").value("VALID"));
    }

    @Test
    void aTicketScannedAtAnotherEventIsWrongEvent() throws Exception {
        long otherEvent = publishedSeatedEvent(organizer, "Other Night");
        AppUserPrincipal admin = new AppUserPrincipal(createUser(Role.ADMIN));

        scan(admin, otherEvent, code)
                .andExpect(jsonPath("$.result").value("WRONG_EVENT"))
                .andExpect(jsonPath("$.message").value("This ticket is for Gate Night"));
        scan(gate, eventId, code).andExpect(jsonPath("$.result").value("VALID"));
    }

    @Test
    void gateStaffCanOnlyScanTheirOwnEvents() throws Exception {
        AppUserPrincipal otherGate = new AppUserPrincipal(createUser(Role.GATE_STAFF));
        scan(otherGate, eventId, code)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        scan(student(), eventId, code).andExpect(status().isForbidden());
    }

    @Test
    void twentyGatesScanningTheSameTicketAtOnceLetItInOnce() throws Exception {
        List<Callable<String>> scans = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            scans.add(() -> read(scan(gate, eventId, code)).get("result").asText());
        }
        List<String> results = runTogether(scans);
        assertThat(results).filteredOn("VALID"::equals).hasSize(1);
        assertThat(results).filteredOn("ALREADY_USED"::equals).hasSize(19);
    }

    @Test
    void everyScanIsLoggedAndCounted() throws Exception {
        scan(gate, eventId, "garbage");
        scan(gate, eventId, code);

        call(get("/api/events/" + eventId + "/checkin/stats"), gate, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.checkedIn").value(1))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.lastScan.result").value("VALID"));
        mvc.perform(get("/api/events/" + eventId + "/checkin/stats").session(organizer))
                .andExpect(status().isOk());
        call(get("/api/events/" + eventId + "/checkin/stats"), student(), null).andExpect(status().isForbidden());

        assertThat(jdbc.queryForList("SELECT result FROM checkin_scans WHERE event_id = ? ORDER BY id", String.class, eventId))
                .containsExactly("INVALID", "VALID");
    }
}
