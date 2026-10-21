package com.festpass.common;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * HMAC-SHA256 signatures for values the server hands out and later trusts again:
 * ticket QR codes and waiting-room admission tokens.
 */
public final class Hmac {

    /** 16 bytes (128 bits) of the HMAC: short enough for a QR code, still unguessable. */
    private static final int SIGNATURE_BYTES = 16;

    private final SecretKeySpec key;

    public Hmac(String secret) {
        if (secret == null || secret.length() < 16) {
            throw new IllegalStateException("The signing secret (QR_SECRET) must be at least 16 characters");
        }
        this.key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    /** URL-safe base64 signature of the payload. */
    public String sign(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            byte[] full = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(Arrays.copyOf(full, SIGNATURE_BYTES));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 is not available", e);
        }
    }

    /** Constant-time check, so timing doesn't reveal how much of a forged signature was right. */
    public boolean verify(String payload, String signature) {
        return signature != null && MessageDigest.isEqual(
                sign(payload).getBytes(StandardCharsets.US_ASCII), signature.getBytes(StandardCharsets.US_ASCII));
    }
}
