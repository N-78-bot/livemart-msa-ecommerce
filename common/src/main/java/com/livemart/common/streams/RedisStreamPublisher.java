package com.livemart.common.streams;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StringRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnBean(StringRedisTemplate.class)
public class RedisStreamPublisher {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RecordId publish(String streamKey, String eventType, Object payload) {
        try {
            String payloadJson = objectMapper.writeValueAsString(payload);
            Map<String, String> fields = Map.of(
                    "eventType", eventType,
                    "payload", payloadJson,
                    "timestamp", String.valueOf(System.currentTimeMillis())
            );

            StringRecord record = StringRecord.of(fields).withStreamKey(streamKey);
            RecordId recordId = redisTemplate.opsForStream().add(record);

            log.debug("Redis Stream published: stream={}, eventType={}, id={}", streamKey, eventType, recordId);
            return recordId;
        } catch (Exception e) {
            log.error("Failed to publish to Redis Stream: stream={}, eventType={}", streamKey, eventType, e);
            throw new IllegalStateException("Redis Stream publish failed", e);
        }
    }

    public void trimStream(String streamKey, long maxLength) {
        redisTemplate.opsForStream().trim(streamKey, maxLength);
        log.debug("Redis Stream trimmed: stream={}, maxLength={}", streamKey, maxLength);
    }
}
