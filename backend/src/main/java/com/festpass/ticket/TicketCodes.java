package com.festpass.ticket;

import com.festpass.common.Hmac;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * The text inside a ticket's QR code: {@code ticketId.eventId.signature} (PLAN.md 5.6).
 * The signature is an HMAC-SHA256 of {@code ticketId.eventId} with a server secret, so a code
 * can't be forged or changed to another event without the secret.
 */
@Component
public class TicketCodes {

    private final Hmac hmac;

    public TicketCodes(@Value("${festpass.qr-secret}") String secret) {
        this.hmac = new Hmac(secret);
    }

    public record Parsed(UUID ticketId, long eventId) {}

    public String codeFor(UUID ticketId, Long eventId) {
        String payload = ticketId + "." + eventId;
        return payload + "." + hmac.sign(payload);
    }

    /** The ticket and event in the code, or empty if the code is malformed or the signature is wrong. */
    public Optional<Parsed> verify(String code) {
        if (code == null) {
            return Optional.empty();
        }
        String[] parts = code.trim().split("\\.");
        if (parts.length != 3) {
            return Optional.empty();
        }
        try {
            UUID ticketId = UUID.fromString(parts[0]);
            long eventId = Long.parseLong(parts[1]);
            return hmac.verify(ticketId + "." + eventId, parts[2])
                    ? Optional.of(new Parsed(ticketId, eventId))
                    : Optional.empty();
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
