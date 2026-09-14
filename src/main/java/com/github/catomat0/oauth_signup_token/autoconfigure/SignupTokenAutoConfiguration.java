package com.github.catomat0.oauth_signup_token.autoconfigure;

import com.github.catomat0.oauth_signup_token.SignupTokenCookieWriter;
import com.github.catomat0.oauth_signup_token.SignupTokenProperties;
import com.github.catomat0.oauth_signup_token.SignupTokenProvider;
import com.github.catomat0.oauth_signup_token.SignupTokenService;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;

@AutoConfiguration(after = RedisAutoConfiguration.class)
@ConditionalOnClass(Jwts.class)
@EnableConfigurationProperties(SignupTokenProperties.class)
public class SignupTokenAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public SignupTokenProvider signupTokenProvider(SignupTokenProperties properties) {
        return new SignupTokenProvider(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(HttpServletResponse.class)
    public SignupTokenCookieWriter signupTokenCookieWriter(SignupTokenProperties properties) {
        return new SignupTokenCookieWriter(properties);
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(RedisTemplate.class)
    @ConditionalOnBean(RedisTemplate.class)
    static class RedisConfiguration {

        @Bean
        @ConditionalOnMissingBean
        SignupTokenService signupTokenService(
                RedisTemplate<String, String> redisTemplate,
                SignupTokenProperties properties
        ) {
            return new SignupTokenService(redisTemplate, properties);
        }
    }
}
