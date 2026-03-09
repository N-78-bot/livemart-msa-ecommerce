package com.livemart.payment.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * AOP 기반 결제 서비스 비즈니스 메트릭 수집
 *
 * Prometheus에 노출되는 커스텀 메트릭:
 * - payments_processed_total{status}        : 결제 처리 성공/실패 카운터
 * - payments_refunded_total{status}         : 환불 처리 성공/실패 카운터
 * - payments_processing_seconds{status}     : 결제 처리 시간 (히스토그램)
 * - payments_amount_summary                 : 결제 금액 분포 (min/max/avg/percentile)
 *
 * Grafana 쿼리 예시:
 *   rate(payments_processed_total{status="success"}[5m])         → 분당 결제 성공률
 *   rate(payments_processed_total{status="failure"}[5m])         → 분당 결제 실패율
 *   payments_amount_summary_sum / payments_amount_summary_count  → 평균 결제 금액
 */
@Slf4j
@Aspect
@Component
public class PaymentMetricsAspect {

    private final Counter paymentSuccess;
    private final Counter paymentFailure;
    private final Counter refundSuccess;
    private final Counter refundFailure;
    private final Timer paymentTimer;
    private final Timer refundTimer;
    private final DistributionSummary amountSummary;

    public PaymentMetricsAspect(MeterRegistry registry) {
        this.paymentSuccess = Counter.builder("payments.processed.total")
                .tag("status", "success")
                .description("Total number of successful payments")
                .register(registry);

        this.paymentFailure = Counter.builder("payments.processed.total")
                .tag("status", "failure")
                .description("Total number of failed payments")
                .register(registry);

        this.refundSuccess = Counter.builder("payments.refunded.total")
                .tag("status", "success")
                .description("Total number of successful refunds")
                .register(registry);

        this.refundFailure = Counter.builder("payments.refunded.total")
                .tag("status", "failure")
                .description("Total number of failed refunds")
                .register(registry);

        this.paymentTimer = Timer.builder("payments.processing.seconds")
                .description("Payment processing duration")
                .publishPercentiles(0.5, 0.95, 0.99)
                .publishPercentileHistogram()
                .register(registry);

        this.refundTimer = Timer.builder("payments.refund.seconds")
                .description("Refund processing duration")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);

        // 결제 금액 분포 통계 (원 단위)
        this.amountSummary = DistributionSummary.builder("payments.amount.summary")
                .description("Distribution of payment amounts (KRW)")
                .baseUnit("KRW")
                .publishPercentiles(0.5, 0.9, 0.99)
                .publishPercentileHistogram()
                .register(registry);
    }

    /**
     * processPayment() 실행 시간 측정 + 성공/실패 카운터
     */
    @Around("execution(* com.livemart.payment.service.PaymentService.processPayment(..))")
    public Object measurePaymentProcessing(ProceedingJoinPoint pjp) throws Throwable {
        long start = System.nanoTime();
        try {
            Object result = pjp.proceed();
            paymentSuccess.increment();

            // 결제 금액 추출 (PaymentRequest.Create 첫 번째 인수)
            Object[] args = pjp.getArgs();
            if (args.length > 0) {
                extractAmount(args[0]);
            }

            log.debug("[Metrics] 결제 성공 - payments.processed.total[success]++");
            return result;
        } catch (Throwable t) {
            paymentFailure.increment();
            log.warn("[Metrics] 결제 실패 - payments.processed.total[failure]++: {}", t.getMessage());
            throw t;
        } finally {
            paymentTimer.record(System.nanoTime() - start, TimeUnit.NANOSECONDS);
        }
    }

    /**
     * refundPayment() 실행 시간 측정 + 성공/실패 카운터
     */
    @Around("execution(* com.livemart.payment.service.PaymentService.refundPayment(..))")
    public Object measureRefundProcessing(ProceedingJoinPoint pjp) throws Throwable {
        long start = System.nanoTime();
        try {
            Object result = pjp.proceed();
            refundSuccess.increment();
            log.debug("[Metrics] 환불 성공 - payments.refunded.total[success]++");
            return result;
        } catch (Throwable t) {
            refundFailure.increment();
            log.warn("[Metrics] 환불 실패 - payments.refunded.total[failure]++: {}", t.getMessage());
            throw t;
        } finally {
            refundTimer.record(System.nanoTime() - start, TimeUnit.NANOSECONDS);
        }
    }

    /**
     * PaymentRequest에서 amount 필드를 리플렉션으로 읽어 DistributionSummary에 기록
     */
    private void extractAmount(Object request) {
        try {
            var method = request.getClass().getMethod("getAmount");
            Object amount = method.invoke(request);
            if (amount instanceof Number num) {
                amountSummary.record(num.doubleValue());
            }
        } catch (Exception ignored) {
            // amount 추출 실패 시 메트릭 수집 생략 (핵심 로직에 영향 없음)
        }
    }
}
