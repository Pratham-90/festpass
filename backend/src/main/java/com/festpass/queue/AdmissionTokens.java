package com.festpass.queue;

import com.festpass.common.Hmac;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Short-lived proof that a user came through a flash drop's waiting room:
 * {@code userId.eventId.expiresAtEpochSeconds.signature}. It is checked without any server
 * state, so it stays valid even if the app restarts while the user is picking seats.
 */
@Component
public class AdmissionTokens {

    private final Hmac hmac;

    public AdmissionTokens(@Value("${festpass.qr-secret}") String secret) {
        this.hmac = new Hmac(secret);
    }

    public String issue(long userId, long eventId, Instant expiresAt) {
        String claims = userId + "." + eventId + "." + expiresAt.getEpochSecond();
        return claims + "." + hmac.sign(payload(claims));
    }

    public boolean isValid(String token, long userId, long eventId, Instant now) {
        if (token == null) {
            return false;
        }
        String[] parts = token.split("\\.");
        if (parts.length != 4) {
            return false;
        }
        try {
            long tokenUser = Long.parseLong(parts[0]);
            long tokenEvent = Long.parseLong(parts[1]);
            long expiresAt = Long.parseLong(parts[2]);
            String claims = tokenUser + "." + tokenEvent + "." + expiresAt;
            return tokenUser == userId
                    && tokenEvent == eventId
                    && now.getEpochSecond() < expiresAt
                    && hmac.verify(payload(claims), parts[3]);
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /** "admission:" keeps these signatures from ever matching a ticket code's. */
    private static String payload(String claims) {
        return "admission:" + claims;
    }
}
