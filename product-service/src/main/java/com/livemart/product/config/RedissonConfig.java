package com.livemart.product.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.URI;

@Configuration
public class RedissonConfig {

    @Value("${spring.data.redis.url:}")
    private String redisUrl;

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    @Bean
    public RedissonClient redissonClient() {
        Config config = new Config();
        SingleServerConfig serverConfig;

        if (redisUrl != null && !redisUrl.isBlank()) {
            try {
                URI uri = new URI(redisUrl);
                boolean tls = "rediss".equals(uri.getScheme());
                String scheme = tls ? "rediss" : "redis";
                String address = scheme + "://" + uri.getHost() + ":" + uri.getPort();

                serverConfig = config.useSingleServer().setAddress(address);

                if (uri.getUserInfo() != null) {
                    String[] userInfo = uri.getUserInfo().split(":", 2);
                    if (userInfo.length == 2) {
                        serverConfig.setPassword(userInfo[1]);
                    }
                }
            } catch (Exception e) {
                serverConfig = config.useSingleServer().setAddress(redisUrl);
            }
        } else {
            serverConfig = config.useSingleServer()
                    .setAddress("redis://" + redisHost + ":" + redisPort);
        }

        serverConfig
                .setConnectionPoolSize(50)
                .setConnectionMinimumIdleSize(10)
                .setIdleConnectionTimeout(10000)
                .setConnectTimeout(10000)
                .setTimeout(3000)
                .setRetryAttempts(3)
                .setRetryInterval(1500);

        return Redisson.create(config);
    }
}
