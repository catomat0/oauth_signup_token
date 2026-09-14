package com.github.catomat0.oauth_signup_token;

import org.springframework.data.redis.core.RedisTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.TimeUnit;

public class SignupTokenService {

    private final RedisTemplate<String, String> redisTemplate;
    private final SignupTokenProperties properties;

    public SignupTokenService(RedisTemplate<String, String> redisTemplate, SignupTokenProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    public void save(String provider, String providerId, String signupToken) {
        redisTemplate.opsForValue().set(
                key(provider, providerId),
                signupToken,
                properties.getExpiration(),
                TimeUnit.MILLISECONDS
        );
    }

    public String get(String provider, String providerId) {
        return redisTemplate.opsForValue().get(key(provider, providerId));
    }

    /**
     * Redis에 저장된 토큰과 일치하는지만 확인 (삭제하지 않음).
     * 온보딩 도중 여러 번 토큰을 참조해야 할 때 사용.
     * <p>회원가입 완료(1회성) 상황에서는 {@link #validateAndConsume} 사용 권장.
     */
    public boolean validate(String provider, String providerId, String signupToken) {
        String saved = get(provider, providerId);
        return timingSafeEquals(saved, signupToken);
    }

    /**
     * Redis에서 토큰을 원자적으로 조회+삭제(GETDEL) 후 일치 여부 반환.
     * 회원가입 완료 시점에 호출하면 동시 요청으로 인한 중복 처리를 방지한다.
     * <p><b>요구사항:</b> Redis 6.2 이상 (GETDEL 지원).
     */
    public boolean validateAndConsume(String provider, String providerId, String signupToken) {
        String saved = redisTemplate.opsForValue().getAndDelete(key(provider, providerId));
        return timingSafeEquals(saved, signupToken);
    }

    public void delete(String provider, String providerId) {
        redisTemplate.delete(key(provider, providerId));
    }

    private String key(String provider, String providerId) {
        return properties.getRedisKeyPrefix() + provider + ":" + providerId;
    }

    private static boolean timingSafeEquals(String a, String b) {
        if (a == null || b == null) return false;
        byte[] aBytes = a.getBytes(StandardCharsets.UTF_8);
        byte[] bBytes = b.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(aBytes, bBytes);
    }
}
