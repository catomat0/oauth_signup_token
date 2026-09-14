package com.github.catomat0.oauth_signup_token;

import java.util.Collections;
import java.util.Map;

public record SignupTokenPayload(
        String provider,
        String providerId,
        String email,
        Map<String, String> extras
) {
    public SignupTokenPayload {
        extras = extras == null ? Collections.emptyMap() : Map.copyOf(extras);
    }

    public String extra(String key) {
        return extras.get(key);
    }
}
