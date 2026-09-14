package com.github.catomat0.oauth_signup_token;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "signup-token")
public class SignupTokenProperties {

    private String secretKey;
    private long expiration = 1_800_000L;
    private String redisKeyPrefix = "ST:";
    private Cookie cookie = new Cookie();

    @PostConstruct
    void validate() {
        if ("None".equalsIgnoreCase(cookie.getSameSite()) && !cookie.isSecure()) {
            throw new IllegalStateException(
                    "signup-token.cookie.same-site=None requires cookie.secure=true "
                            + "(browsers drop the cookie otherwise). "
                            + "For HTTP dev environments, use same-site=Lax and secure=false.");
        }
        if (expiration <= 0) {
            throw new IllegalStateException(
                    "signup-token.expiration must be positive (millis); got " + expiration);
        }
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public long getExpiration() {
        return expiration;
    }

    public void setExpiration(long expiration) {
        this.expiration = expiration;
    }

    public String getRedisKeyPrefix() {
        return redisKeyPrefix;
    }

    public void setRedisKeyPrefix(String redisKeyPrefix) {
        this.redisKeyPrefix = redisKeyPrefix;
    }

    public Cookie getCookie() {
        return cookie;
    }

    public void setCookie(Cookie cookie) {
        this.cookie = cookie;
    }

    @Override
    public String toString() {
        return "SignupTokenProperties{"
                + "secretKey=" + (secretKey == null || secretKey.isEmpty() ? "[UNSET]" : "[MASKED]")
                + ", expiration=" + expiration
                + ", redisKeyPrefix='" + redisKeyPrefix + '\''
                + ", cookie=" + cookie
                + '}';
    }

    public static class Cookie {
        private String name = "signup_token";
        private String path = "/";
        private String domain;
        private boolean httpOnly = true;
        private boolean secure = true;
        private String sameSite = "None";

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getPath() { return path; }
        public void setPath(String path) { this.path = path; }

        public String getDomain() { return domain; }
        public void setDomain(String domain) { this.domain = domain; }

        public boolean isHttpOnly() { return httpOnly; }
        public void setHttpOnly(boolean httpOnly) { this.httpOnly = httpOnly; }

        public boolean isSecure() { return secure; }
        public void setSecure(boolean secure) { this.secure = secure; }

        public String getSameSite() { return sameSite; }
        public void setSameSite(String sameSite) { this.sameSite = sameSite; }

        @Override
        public String toString() {
            return "Cookie{name='" + name + "', path='" + path + "', domain='" + domain
                    + "', httpOnly=" + httpOnly + ", secure=" + secure
                    + ", sameSite='" + sameSite + "'}";
        }
    }
}
