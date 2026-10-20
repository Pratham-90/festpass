package com.festpass.common;

import java.util.regex.Pattern;

/** Checks the Idempotency-Key header sent with orders and payments. The client makes one key per attempt (e.g. a UUID). */
public final class IdempotencyKeys {

    public static final String HEADER = "Idempotency-Key";

    private static final Pattern VALID = Pattern.compile("^[A-Za-z0-9_.:-]{8,100}$");

    private IdempotencyKeys() {}

    public static String require(String key) {
        if (key == null || !VALID.matcher(key).matches()) {
            throw ApiException.validation(HEADER, "must be 8 to 100 letters, digits or _ . : -");
        }
        return key;
    }
}
