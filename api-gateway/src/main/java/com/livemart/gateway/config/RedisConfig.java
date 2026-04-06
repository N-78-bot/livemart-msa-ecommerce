package com.livemart.gateway.config;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.net.URI;
import java.time.Duration;

/**
 * API Gateway Redis 연결 설정
 *
 * Kubernetes에서 Lettuce SharedConnection 모드의 InterruptedException 버그 수정:
 * - Spring Boot 자동 구성의 redisConnectionFactory 빈을 오버라이드
 * - shareNativeConnection = false → 연결 공유 비활성화 (각 요청마다 독립 연결)
 * - connectTimeout = 2s → 빠른 연결 실패 감지
 */
@Configuration
public class RedisConfig {

    @Value("${spring.data.redis.url:}")
    private String redisUrl;

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    @Bean
    @Primary
    public LettuceConnectionFactory redisConnectionFactory() {
        RedisStandaloneConfiguration serverConfig;
        boolean useSsl = false;

        if (redisUrl != null && !redisUrl.isBlank()) {
            try {
                URI uri = new URI(redisUrl);
                useSsl = "rediss".equals(uri.getScheme());
                serverConfig = new RedisStandaloneConfiguration(uri.getHost(), uri.getPort());
                if (uri.getUserInfo() != null) {
                    String[] userInfo = uri.getUserInfo().split(":", 2);
                    if (userInfo.length == 2) {
                        serverConfig.setPassword(RedisPassword.of(userInfo[1]));
                    }
                }
            } catch (Exception e) {
                serverConfig = new RedisStandaloneConfiguration(redisHost, redisPort);
            }
        } else {
            serverConfig = new RedisStandaloneConfiguration(redisHost, redisPort);
        }

        LettuceClientConfiguration.LettuceClientConfigurationBuilder builder =
                LettuceClientConfiguration.builder()
                        .clientOptions(ClientOptions.builder()
                                .socketOptions(SocketOptions.builder()
                                        .connectTimeout(Duration.ofSeconds(2))
                                        .build())
                                .build())
                        .commandTimeout(Duration.ofSeconds(5));

        if (useSsl) {
            builder.useSsl().disablePeerVerification();
        }

        LettuceConnectionFactory factory = new LettuceConnectionFactory(serverConfig, builder.build());
        factory.setShareNativeConnection(false);
        factory.setValidateConnection(false);
        factory.afterPropertiesSet();
        return factory;
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate(LettuceConnectionFactory redisConnectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(redisConnectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.afterPropertiesSet();
        return template;
    }
}
