package com.livemart.order.metrics;

import com.livemart.order.dto.OrderResponse;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * AOP 기반 주문 서비스 비즈니스 메트릭 수집
 *
 * Prometheus에 노출되는 커스텀 메트릭:
 * - orders_created_total{status}       : 주문 생성 성공/실패 카운터
 * - orders_cancelled_total             : 주문 취소 카운터
 * - orders_confirmed_total             : 주문 확정 카운터 (결제 완료 후)
 * - orders_processing_seconds{status}  : 주문 생성 처리 시간 (히스토그램)
 * - orders_active_gauge                : 현재 처리 중인 주문 수
 *
 * Grafana 쿼리 예시:
 *   rate(orders_created_total{status="success"}[5m])  → 분당 주문 생성률
 *   histogram_quantile(0.99, orders_processing_seconds_bucket) → P99 처리 시간
 */
@Slf4j
@Aspect
@Component
public class OrderMetricsAspect {

    private final Counter orderCreatedSuccess;
    private final Counter orderCreatedFailure;
    private final Counter orderCancelled;
    private final Counter orderConfirmed;
    private final Timer orderCreationTimer;
    private final AtomicLong activeOrders = new AtomicLong(0);

    public OrderMetricsAspect(MeterRegistry registry) {
        this.orderCreatedSuccess = Counter.builder("orders.created.total")
                .tag("status", "success")
                .description("Total number of successfully created orders")
                .register(registry);

        this.orderCreatedFailure = Counter.builder("orders.created.total")
                .tag("status", "failure")
                .description("Total number of failed order creations")
                .register(registry);

        this.orderCancelled = Counter.builder("orders.cancelled.total")
                .description("Total number of cancelled orders")
                .register(registry);

        this.orderConfirmed = Counter.builder("orders.confirmed.total")
                .description("Total number of confirmed orders (after payment)")
                .register(registry);

        this.orderCreationTimer = Timer.builder("orders.processing.seconds")
                .description("Order creation processing time")
                .publishPercentiles(0.5, 0.95, 0.99)
                .publishPercentileHistogram()
                .register(registry);

        // Gauge: 현재 처리 중인 주문 수
        registry.gauge("orders.active.gauge", activeOrders);
    }

    /**
     * createOrder() 메서드 실행 시간 측정 + 성공/실패 카운터
     */
    @Around("execution(* com.livemart.order.service.OrderService.createOrder(..))")
    public Object measureOrderCreation(ProceedingJoinPoint pjp) throws Throwable {
        activeOrders.incrementAndGet();
        long start = System.nanoTime();
        try {
            Object result = pjp.proceed();
            orderCreatedSuccess.increment();
            log.debug("[Metrics] 주문 생성 성공 - orders.created.total[success]++");
            return result;
        } catch (Throwable t) {
            orderCreatedFailure.increment();
            log.warn("[Metrics] 주문 생성 실패 - orders.created.total[failure]++: {}", t.getMessage());
            throw t;
        } finally {
            orderCreationTimer.record(System.nanoTime() - start, TimeUnit.NANOSECONDS);
            activeOrders.decrementAndGet();
        }
    }

    /**
     * cancelOrder() 호출 시 취소 카운터 증가
     */
    @Around("execution(* com.livemart.order.service.OrderService.cancelOrder(..))")
    public Object measureOrderCancellation(ProceedingJoinPoint pjp) throws Throwable {
        Object result = pjp.proceed();
        orderCancelled.increment();
        log.debug("[Metrics] 주문 취소 - orders.cancelled.total++");
        return result;
    }

    /**
     * confirmOrder() 호출 시 확정 카운터 증가 (Kafka 결제 완료 이벤트 수신 후)
     */
    @Around("execution(* com.livemart.order.service.OrderService.confirmOrder(..))")
    public Object measureOrderConfirmation(ProceedingJoinPoint pjp) throws Throwable {
        Object result = pjp.proceed();
        orderConfirmed.increment();
        log.debug("[Metrics] 주문 확정 - orders.confirmed.total++");
        return result;
    }
}
