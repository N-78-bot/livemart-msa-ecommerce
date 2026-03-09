/**
 * Spring Cloud Contract — payment-service Provider 계약
 *
 * 이 파일이 정의하는 것:
 *   - order-service(Consumer)가 payment-service(Provider)에 기대하는 API 계약
 *   - payment-service 코드 변경 시 계약 위반 자동 감지 (CI에서 실패)
 *
 * 계약 흐름:
 *   order-service → POST /api/v1/payments → payment-service
 */

import org.springframework.cloud.contract.spec.Contract

// 계약 1: 정상 결제 처리
Contract.make {
    description "POST /api/v1/payments — 주문에 대해 결제를 성공적으로 처리한다"

    request {
        method POST()
        url '/api/v1/payments'
        headers {
            contentType(applicationJson())
        }
        body(
            orderId:       $(consumer(anyPositiveInt()), producer(1L)),
            orderNumber:   $(consumer(anyNonEmptyString()), producer("ORD-20260101-TEST0001")),
            userId:        $(consumer(anyPositiveInt()), producer(100L)),
            amount:        $(consumer(anyPositiveInt()), producer(50000)),
            paymentMethod: $(consumer(anyOf('CREDIT_CARD','DEBIT_CARD','DIGITAL_WALLET')), producer("CREDIT_CARD"))
        )
    }

    response {
        status CREATED()
        headers {
            contentType(applicationJson())
        }
        body(
            id:              $(producer(anyPositiveInt()), consumer(anyPositiveInt())),
            orderNumber:     $(producer(anyNonEmptyString()), consumer(anyNonEmptyString())),
            transactionId:   $(producer(anyNonEmptyString()), consumer(anyNonEmptyString())),
            status:          "COMPLETED",
            amount:          $(producer(anyPositiveInt()), consumer(anyPositiveInt())),
            paymentMethod:   $(producer(anyNonEmptyString()), consumer(anyNonEmptyString())),
            createdAt:       $(producer(anyIso8601WithOffset()), consumer(anyIso8601WithOffset()))
        )
    }
}
