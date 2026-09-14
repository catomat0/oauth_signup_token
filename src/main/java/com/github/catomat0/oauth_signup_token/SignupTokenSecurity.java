package com.github.catomat0.oauth_signup_token;

import org.springframework.http.HttpHeaders;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

public final class SignupTokenSecurity {

    private SignupTokenSecurity() {}

    public static CorsConfigurationSource corsForCookieAuth(String... allowedOriginPatterns) {
        return corsForCookieAuth(Arrays.asList(allowedOriginPatterns));
    }

    public static CorsConfigurationSource corsForCookieAuth(List<String> allowedOriginPatterns) {
        return corsForCookieAuth(allowedOriginPatterns, "/**");
    }

    public static CorsConfigurationSource corsForCookieAuth(List<String> allowedOriginPatterns, String pathPattern) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowCredentials(true);
        config.setAllowedOriginPatterns(allowedOriginPatterns);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.addAllowedHeader(CorsConfiguration.ALL);
        config.setExposedHeaders(List.of(HttpHeaders.SET_COOKIE));
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration(pathPattern, config);
        return source;
    }

    public static CorsConfiguration cookieAuthCorsConfig(List<String> allowedOriginPatterns) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowCredentials(true);
        config.setAllowedOriginPatterns(allowedOriginPatterns);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.addAllowedHeader(CorsConfiguration.ALL);
        config.setExposedHeaders(List.of(HttpHeaders.SET_COOKIE));
        config.setMaxAge(3600L);
        return config;
    }
}
