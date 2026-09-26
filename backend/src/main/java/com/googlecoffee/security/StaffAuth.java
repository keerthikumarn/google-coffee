package com.googlecoffee.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Deliberately simple staff auth: one café PIN exchanged for a signed,
 * expiring token (HMAC-SHA256). Includes a small per-client lockout to slow
 * down PIN guessing.
 */
public class StaffAuth {

    private static final long TOKEN_TTL_MS = 12 * 60 * 60 * 1000L;
    private static final int MAX_FAILURES = 5;
    private static final long LOCKOUT_MS = 5 * 60 * 1000L;

    private final byte[] pin;
    private final byte[] secret;
    private final Map<String, long[]> failures = new ConcurrentHashMap<>(); // client -> [count, firstFailureAt]

    public StaffAuth(String pin, String secret) {
        if (pin == null || pin.isBlank()) throw new IllegalArgumentException("STAFF_PIN must be set");
        if (secret == null || secret.length() < 16) throw new IllegalArgumentException("STAFF_TOKEN_SECRET must be at least 16 characters");
        this.pin = pin.getBytes(StandardCharsets.UTF_8);
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    public boolean isLockedOut(String client, long now) {
        long[] f = failures.get(client);
        if (f == null) return false;
        if (now - f[1] > LOCKOUT_MS) {
            failures.remove(client);
            return false;
        }
        return f[0] >= MAX_FAILURES;
    }

    /** Returns a token on success, or null when the PIN is wrong. */
    public String login(String client, String candidatePin, long now) {
        boolean ok = candidatePin != null
                && MessageDigest.isEqual(pin, candidatePin.getBytes(StandardCharsets.UTF_8));
        if (!ok) {
            failures.compute(client, (k, v) -> v == null || now - v[1] > LOCKOUT_MS ? new long[]{1, now} : new long[]{v[0] + 1, v[1]});
            return null;
        }
        failures.remove(client);
        long expiry = now + TOKEN_TTL_MS;
        return expiry + "." + sign(Long.toString(expiry));
    }

    public boolean verify(String token, long now) {
        if (token == null) return false;
        int dot = token.indexOf('.');
        if (dot <= 0) return false;
        String expiryPart = token.substring(0, dot);
        long expiry;
        try {
            expiry = Long.parseLong(expiryPart);
        } catch (NumberFormatException e) {
            return false;
        }
        if (expiry < now) return false;
        byte[] expected = sign(expiryPart).getBytes(StandardCharsets.UTF_8);
        byte[] given = token.substring(dot + 1).getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, given);
    }

    private String sign(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            byte[] sig = mac.doFinal(("staff:" + payload).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(sig);
        } catch (Exception e) {
            throw new IllegalStateException("HMAC unavailable", e);
        }
    }
}
