package com.github.catomat0.oauth_signup_token;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class SignupTokenProvider {

    private static final String TYPE_VALUE = "SIGNUP";
    private static final String CLAIM_TYPE = "type";
    private static final String CLAIM_PROVIDER = "provider";
    private static final String CLAIM_PROVIDER_ID = "providerId";
    private static final String CLAIM_EMAIL = "email";
    private static final int MIN_SECRET_BYTES = 32;

    private static final Set<String> RESERVED_CLAIMS = Set.of(
            CLAIM_TYPE, CLAIM_PROVIDER, CLAIM_PROVIDER_ID, CLAIM_EMAIL,
            "iat", "exp", "nbf", "iss", "sub", "aud", "jti"
    );

    private final SignupTokenProperties properties;
    private final SecretKey signingKey;

    public SignupTokenProvider(SignupTokenProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties");
        this.signingKey = buildSigningKey(properties.getSecretKey());
    }

    private static SecretKey buildSigningKey(String secretKey) {
        if (secretKey == null || secretKey.isBlank()) {
            throw new IllegalStateException(
                    "signup-token.secret-key must be configured");
        }
        byte[] keyBytes = secretKey.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "signup-token.secret-key must be at least " + MIN_SECRET_BYTES
                            + " bytes (256 bits) for HS256; got " + keyBytes.length + " bytes. "
                            + "Generate one with: openssl rand -base64 48");
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public String generate(String provider, String providerId, String email) {
        return doGenerate(provider, providerId, email, Collections.emptyMap(), null);
    }

    public String generate(String provider, String providerId, String email, Map<String, String> extras) {
        return doGenerate(provider, providerId, email, extras, null);
    }

    public String generate(String provider, String providerId, String email, Map<String, String> extras, long ttlMillis) {
        return doGenerate(provider, providerId, email, extras, ttlMillis);
    }

    public String generate(String provider, String providerId, String email, Map<String, String> extras, Duration ttl) {
        return doGenerate(provider, providerId, email, extras, ttl == null ? null : ttl.toMillis());
    }

    public SignupTokenBuilder builder(String provider, String providerId, String email) {
        return new SignupTokenBuilder(this, provider, providerId, email);
    }

    String doGenerate(String provider, String providerId, String email,
                      Map<String, String> extras, Long ttlMillisOverride) {
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(providerId, "providerId");
        Objects.requireNonNull(email, "email");

        long ttl = ttlMillisOverride != null ? ttlMillisOverride : properties.getExpiration();
        Date now = new Date();
        Date expiry = new Date(now.getTime() + ttl);

        var builder = Jwts.builder()
                .claim(CLAIM_TYPE, TYPE_VALUE)
                .claim(CLAIM_PROVIDER, provider)
                .claim(CLAIM_PROVIDER_ID, providerId)
                .claim(CLAIM_EMAIL, email)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(signingKey);

        if (extras != null) {
            for (Map.Entry<String, String> e : extras.entrySet()) {
                String key = e.getKey();
                String value = e.getValue();
                if (key == null || RESERVED_CLAIMS.contains(key)) continue;
                if (value == null || value.isBlank()) continue;
                builder.claim(key, value);
            }
        }

        return builder.compact();
    }

    public boolean validate(String token) {
        if (token == null || token.isBlank()) return false;
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return TYPE_VALUE.equals(claims.get(CLAIM_TYPE, String.class));
        } catch (Exception e) {
            return false;
        }
    }

    public SignupTokenPayload parse(String token) {
        Objects.requireNonNull(token, "token");
        Claims claims = Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        String type = claims.get(CLAIM_TYPE, String.class);
        if (!TYPE_VALUE.equals(type)) {
            throw new IllegalArgumentException(
                    "Token is not a signup token (expected type=" + TYPE_VALUE + ", got type=" + type + ")");
        }

        String provider = claims.get(CLAIM_PROVIDER, String.class);
        String providerId = claims.get(CLAIM_PROVIDER_ID, String.class);
        String email = claims.get(CLAIM_EMAIL, String.class);

        Map<String, String> extras = new HashMap<>();
        for (Map.Entry<String, Object> entry : claims.entrySet()) {
            if (RESERVED_CLAIMS.contains(entry.getKey())) continue;
            if (entry.getValue() instanceof String s) {
                extras.put(entry.getKey(), s);
            }
        }

        return new SignupTokenPayload(provider, providerId, email, extras);
    }
}
