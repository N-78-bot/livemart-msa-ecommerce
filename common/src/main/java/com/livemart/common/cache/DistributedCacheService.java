package com.livemart.common.cache;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 분산 캐시 서비스
 *
 * Redis Cluster 기반 고급 캐싱 전략:
 * 1. Cache-Aside Pattern
 * 2. Write-Through Pattern
 * 3. Cache Warming (사전 로딩)
 * 4. Cache Stampede 방지
 * 5. Probabilistic Early Expiration
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DistributedCacheService {

    private final RedisTemplate<String, Object> redisTemplate;

    private static final String LOCK_PREFIX = "lock:";
    private static final Duration DEFAULT_TTL = Duration.ofHours(1);
    private static final Duration LOCK_TTL = Duration.ofSeconds(10);

    /**
     * Cache-Aside Pattern
     * 캐시 미스 시 DB 조회 후 캐시 저장
     */
    public <T> T getOrLoad(String key, Supplier<T> loader, Duration ttl) {
        // 1. 캐시 조회
        T cached = (T) redisTemplate.opsForValue().get(key);

        if (cached != null) {
            log.debug("Cache hit: key={}", key);
            return cached;
        }

        log.debug("Cache miss: key={}", key);

        // 2. Cache Stampede 방지 (분산 락)
        String lockKey = LOCK_PREFIX + key;
        Boolean acquired = redisTemplate.opsForValue()
            .setIfAbsent(lockKey, "1", LOCK_TTL.getSeconds(), TimeUnit.SECONDS);

        if (Boolean.TRUE.equals(acquired)) {
            try {
                // 락을 획득한 스레드만 DB 조회
                T value = loader.get();

                if (value != null) {
                    // 캐시 저장
                    redisTemplate.opsForValue().set(key, value, ttl.getSeconds(), TimeUnit.SECONDS);
                    log.debug("Cache loaded: key={}", key);
                }

                return value;
            } finally {
                // 락 해제
                redisTemplate.delete(lockKey);
            }
        } else {
            // 다른 스레드가 로딩 중: 최대 5회 폴링 후 직접 로딩 (스택 오버플로우 방지)
            for (int i = 0; i < 5; i++) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
                Object cached = redisTemplate.opsForValue().get(key);
                if (cached != null) {
                    log.debug("Cache hit after wait: key={}", key);
                    @SuppressWarnings("unchecked")
                    T result = (T) cached;
                    return result;
                }
            }
            // 타임아웃: 직접 DB 조회 (캐시 없이)
            log.warn("Cache lock timeout, loading directly: key={}", key);
            return loader.get();
        }
    }

    /**
     * Write-Through Pattern
     * 데이터 쓰기 시 캐시와 DB 동시 업데이트
     */
    public <T> void writeThrough(String key, T value, Duration ttl) {
        // 캐시 저장
        redisTemplate.opsForValue().set(key, value, ttl.getSeconds(), TimeUnit.SECONDS);
        log.debug("Cache write-through: key={}", key);

        // DB 저장은 호출자가 수행 (트랜잭션 관리)
    }

    /**
     * Probabilistic Early Expiration
     * 만료 임박 시 확률적으로 미리 갱신
     */
    public <T> T getWithEarlyExpiration(String key, Supplier<T> loader,
                                         Duration ttl, double beta) {

        T cached = (T) redisTemplate.opsForValue().get(key);

        if (cached != null) {
            // TTL 확인
            Long remainingTtl = redisTemplate.getExpire(key, TimeUnit.SECONDS);

            if (remainingTtl != null && remainingTtl > 0) {
                // 확률적 만료 계산
                double delta = Math.log(Math.random()) * beta * -1;
                double xfetch = delta * 1.0; // 로드 시간 가정

                if (remainingTtl < xfetch) {
                    // 미리 갱신
                    log.debug("Early cache refresh: key={}, remainingTtl={}", key, remainingTtl);
                    T freshValue = loader.get();
                    redisTemplate.opsForValue().set(key, freshValue, ttl.getSeconds(), TimeUnit.SECONDS);
                    return freshValue;
                }
            }

            return cached;
        }

        // 캐시 미스
        return getOrLoad(key, loader, ttl);
    }

    /**
     * Multi-Get (배치 조회)
     */
    public <T> Map<String, T> multiGet(List<String> keys) {
        List<Object> values = redisTemplate.opsForValue().multiGet(keys);
        Map<String, T> result = new HashMap<>();

        if (values != null) {
            for (int i = 0; i < keys.size(); i++) {
                if (values.get(i) != null) {
                    result.put(keys.get(i), (T) values.get(i));
                }
            }
        }

        log.debug("Multi-get: requested={}, found={}", keys.size(), result.size());

        return result;
    }

    /**
     * Multi-Set (배치 저장)
     */
    public void multiSet(Map<String, Object> keyValues, Duration ttl) {
        redisTemplate.opsForValue().multiSet(keyValues);

        // TTL 설정 (개별)
        keyValues.keySet().forEach(key ->
            redisTemplate.expire(key, ttl.getSeconds(), TimeUnit.SECONDS)
        );

        log.debug("Multi-set: count={}", keyValues.size());
    }

    /**
     * Cache Warming (캐시 사전 로딩)
     */
    public <T> void warmUp(Map<String, T> data, Duration ttl) {
        data.forEach((key, value) -> {
            redisTemplate.opsForValue().set(key, value, ttl.getSeconds(), TimeUnit.SECONDS);
        });

        log.info("Cache warmed up: count={}", data.size());
    }

    /**
     * 캐시 무효화 (패턴 기반)
     */
    public long invalidateByPattern(String pattern) {
        Set<String> keys = redisTemplate.keys(pattern);

        if (keys != null && !keys.isEmpty()) {
            Long deleted = redisTemplate.delete(keys);
            log.info("Cache invalidated: pattern={}, count={}", pattern, deleted);
            return deleted != null ? deleted : 0;
        }

        return 0;
    }

    /**
     * 단일 캐시 무효화
     */
    public void invalidate(String key) {
        redisTemplate.delete(key);
        log.debug("Cache invalidated: key={}", key);
    }

    /**
     * 캐시 존재 여부
     */
    public boolean exists(String key) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    /**
     * TTL 조회
     */
    public long getTtl(String key) {
        Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        return ttl != null ? ttl : -1;
    }

    /**
     * TTL 갱신
     */
    public void refreshTtl(String key, Duration newTtl) {
        redisTemplate.expire(key, newTtl.getSeconds(), TimeUnit.SECONDS);
        log.debug("Cache TTL refreshed: key={}, ttl={}", key, newTtl);
    }

    /**
     * 카운터 증가 (Rate Limiting, 통계 등)
     */
    public long increment(String key, long delta, Duration ttl) {
        Long result = redisTemplate.opsForValue().increment(key, delta);

        // 첫 증가 시 TTL 설정
        if (result != null && result == delta) {
            redisTemplate.expire(key, ttl.getSeconds(), TimeUnit.SECONDS);
        }

        return result != null ? result : 0;
    }

    /**
     * 카운터 감소
     */
    public long decrement(String key, long delta) {
        Long result = redisTemplate.opsForValue().decrement(key, delta);
        return result != null ? result : 0;
    }

    /**
     * 캐시 통계
     */
    public CacheStats getStats(String pattern) {
        Set<String> keys = redisTemplate.keys(pattern);
        long totalKeys = keys != null ? keys.size() : 0;

        long totalMemory = 0;
        long expiredCount = 0;

        if (keys != null) {
            for (String key : keys) {
                Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
                if (ttl != null && ttl < 0) {
                    expiredCount++;
                }
            }
        }

        return new CacheStats(
            totalKeys,
            totalMemory,
            expiredCount,
            totalKeys - expiredCount
        );
    }

    // Record

    public record CacheStats(
        long totalKeys,
        long totalMemoryBytes,
        long expiredKeys,
        long activeKeys
    ) {}
}
