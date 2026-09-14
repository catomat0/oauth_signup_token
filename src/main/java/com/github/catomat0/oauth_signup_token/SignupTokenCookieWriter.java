package com.github.catomat0.oauth_signup_token;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;

import java.time.Duration;

public class SignupTokenCookieWriter {

    private final SignupTokenProperties properties;

    public SignupTokenCookieWriter(SignupTokenProperties properties) {
        this.properties = properties;
    }

    public void write(HttpServletResponse response, String token) {
        write(response, token, new CookieOptions());
    }

    public void write(HttpServletResponse response, String token, Duration maxAge) {
        write(response, token, new CookieOptions().maxAge(maxAge));
    }

    public void write(HttpServletResponse response, String token, CookieOptions options) {
        response.addHeader(HttpHeaders.SET_COOKIE, buildCookie(token, options).toString());
    }

    public String read(HttpServletRequest request) {
        return read(request, properties.getCookie().getName());
    }

    public String read(HttpServletRequest request, String cookieName) {
        if (request.getCookies() == null) return null;
        for (Cookie cookie : request.getCookies()) {
            if (cookieName.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    public void clear(HttpServletResponse response) {
        clear(response, new CookieOptions());
    }

    public void clear(HttpServletResponse response, CookieOptions options) {
        response.addHeader(HttpHeaders.SET_COOKIE,
                buildCookie("", options.copy().maxAge(Duration.ZERO)).toString());
    }

    private ResponseCookie buildCookie(String value, CookieOptions options) {
        SignupTokenProperties.Cookie c = properties.getCookie();
        String name = options.name != null ? options.name : c.getName();
        String path = options.path != null ? options.path : c.getPath();
        String domain = options.domain != null ? options.domain : c.getDomain();
        boolean httpOnly = options.httpOnly != null ? options.httpOnly : c.isHttpOnly();
        boolean secure = options.secure != null ? options.secure : c.isSecure();
        String sameSite = options.sameSite != null ? options.sameSite : c.getSameSite();
        Duration maxAge = options.maxAge != null ? options.maxAge : Duration.ofMillis(properties.getExpiration());

        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(name, value)
                .httpOnly(httpOnly)
                .secure(secure)
                .sameSite(sameSite)
                .path(path)
                .maxAge(maxAge);
        if (domain != null && !domain.isBlank()) {
            builder.domain(domain);
        }
        return builder.build();
    }

    public static class CookieOptions {
        private String name;
        private String path;
        private String domain;
        private Boolean httpOnly;
        private Boolean secure;
        private String sameSite;
        private Duration maxAge;

        public CookieOptions name(String v) { this.name = v; return this; }
        public CookieOptions path(String v) { this.path = v; return this; }
        public CookieOptions domain(String v) { this.domain = v; return this; }
        public CookieOptions httpOnly(boolean v) { this.httpOnly = v; return this; }
        public CookieOptions secure(boolean v) { this.secure = v; return this; }
        public CookieOptions sameSite(String v) { this.sameSite = v; return this; }
        public CookieOptions maxAge(Duration v) { this.maxAge = v; return this; }

        CookieOptions copy() {
            CookieOptions o = new CookieOptions();
            o.name = name; o.path = path; o.domain = domain;
            o.httpOnly = httpOnly; o.secure = secure; o.sameSite = sameSite;
            o.maxAge = maxAge;
            return o;
        }
    }
}
