package com.festpass.ticket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.festpass.event.Event;
import com.festpass.event.EventEnums.SeatingMode;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

/** Plain unit tests: no Spring, no database. */
class TicketCodesTest {

    private final TicketCodes codes = new TicketCodes("a-test-secret-of-decent-length");

    @Test
    void aSignedCodeVerifies() {
        UUID ticket = UUID.randomUUID();
        String code = codes.codeFor(ticket, 42L);
        assertThat(code).startsWith(ticket + ".42.");
        assertThat(codes.verify(code)).contains(new TicketCodes.Parsed(ticket, 42L));
    }

    @Test
    void anyChangeBreaksTheSignature() {
        UUID ticket = UUID.randomUUID();
        String code = codes.codeFor(ticket, 42L);
        assertThat(codes.verify(code.replace(".42.", ".43."))).isEmpty();
        assertThat(codes.verify(UUID.randomUUID() + code.substring(36))).isEmpty();
        assertThat(codes.verify(code + "x")).isEmpty();
        assertThat(codes.verify("not.a.code")).isEmpty();
        assertThat(codes.verify(null)).isEmpty();
    }

    @Test
    void anotherSecretCannotVerify() {
        String code = codes.codeFor(UUID.randomUUID(), 1L);
        assertThat(new TicketCodes("a-completely-different-secret").verify(code)).isEmpty();
    }

    @Test
    void shortSecretsAreRejected() {
        assertThatThrownBy(() -> new TicketCodes("short")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void calendarFileEscapesAndFolds() {
        Event event = new Event(1L, "slug", SeatingMode.GENERAL, Instant.now());
        ReflectionTestUtils.setField(event, "id", 7L);
        event.setTitle("Rock, Paper; Scissors");
        event.setVenueName("Hall 1");
        event.setStartsAt(Instant.parse("2026-11-01T13:30:00Z"));
        event.setEndsAt(Instant.parse("2026-11-01T16:30:00Z"));

        String ics = CalendarFiles.forEvent(event, "x".repeat(200), Instant.parse("2026-10-20T00:00:00Z"));
        assertThat(ics).contains("SUMMARY:Rock\\, Paper\\; Scissors\r\n")
                .contains("DTSTART:20261101T133000Z\r\n")
                .contains("UID:event-7@festpass");
        assertThat(ics.split("\r\n")).allSatisfy(line -> assertThat(line.length()).isLessThanOrEqualTo(75));
    }
}
