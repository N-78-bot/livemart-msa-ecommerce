package com.livemart.product.aspect;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class DistributedLockAspect {

    private final RedissonClient redissonClient;
    private final ExpressionParser parser = new SpelExpressionParser();

    @Around("@annotation(com.livemart.product.aspect.DistributedLock)")
    public Object lock(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        DistributedLock distributedLock = method.getAnnotation(DistributedLock.class);

        String key = createKey(signature, distributedLock.key(), joinPoint.getArgs());
        RLock lock = redissonClient.getLock(key);

        try {
            boolean available = lock.tryLock(
                    distributedLock.waitTime(),
                    distributedLock.leaseTime(),
                    distributedLock.timeUnit()
            );

            if (!available) {
                log.error("Lock acquisition failed: {}", key);
                throw new RuntimeException("현재 처리 중인 요청이 있습니다. 잠시 후 다시 시도해주세요.");
            }

            log.info("Lock acquired: {}", key);
            return joinPoint.proceed();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("락 획득 중 인터럽트 발생", e);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.info("Lock released: {}", key);
            }
        }
    }

    private String createKey(MethodSignature signature, String keyExpression, Object[] args) {
        String[] parameterNames = signature.getParameterNames();

        StandardEvaluationContext context = new StandardEvaluationContext();
        for (int i = 0; i < parameterNames.length; i++) {
            context.setVariable(parameterNames[i], args[i]);
        }

        try {
            Object value = parser.parseExpression(keyExpression).getValue(context);
            String lockKey = String.format("lock:stock:%s", value);
            log.info("Generated lock key: {}", lockKey);
            return lockKey;
        } catch (Exception e) {
            log.error("Failed to parse key expression: {}", keyExpression, e);
            return "lock:stock:default";
        }
    }
}