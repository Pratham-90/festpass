package com.festpass.ticket;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * The text inside a ticket's QR code: {@code ticketId.eventId.signature} (PLAN.md 5.6).
 * The signature is an HMAC-SHA256 of {@code ticketId.eventId} with a server secret, so a code
 * can't be forged or changed to another event without the secret.
 */
@Component
public class TicketCodes {

    /** 16 bytes (128 bits) of the HMAC, which keeps the QR code small and is still unguessable. */
    private static final int SIGNATURE_BYTES = 16;

    private final SecretKeySpec key;

    public TicketCodes(@Value("${festpass.qr-secret}") String secret) {
        if (secret == null || secret.length() < 16) {
            throw new IllegalStateException("festpass.qr-secret (QR_SECRET) must be at least 16 characters");
        }
        this.key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    public record Parsed(UUID ticketId, long eventId) {}

    public String codeFor(UUID ticketId, Long eventId) {
        String payload = ticketId + "." + eventId;
        return payload + "." + sign(payload);
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
            byte[] expected = sign(ticketId + "." + eventId).getBytes(StandardCharsets.US_ASCII);
            byte[] actual = parts[2].getBytes(StandardCharsets.US_ASCII);
            // Constant-time comparison, so timing doesn't reveal how much of a forged signature was right.
            return MessageDigest.isEqual(expected, actual) ? Optional.of(new Parsed(ticketId, eventId)) : Optional.empty();
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private String sign(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            byte[] full = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(Arrays.copyOf(full, SIGNATURE_BYTES));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 is not available", e);
        }
    }
}
