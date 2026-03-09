import org.springframework.cloud.contract.spec.Contract

// 계약 2: 주문 번호로 결제 조회
Contract.make {
    description "GET /api/v1/payments/order/{orderNumber} — 주문번호로 결제 정보를 조회한다"

    request {
        method GET()
        urlPath('/api/v1/payments/order/ORD-20260101-TEST0001')
    }

    response {
        status OK()
        headers {
            contentType(applicationJson())
        }
        body(
            id:            $(producer(anyPositiveInt()), consumer(anyPositiveInt())),
            orderNumber:   "ORD-20260101-TEST0001",
            transactionId: $(producer(anyNonEmptyString()), consumer(anyNonEmptyString())),
            status:        $(producer(anyOf('PENDING','COMPLETED','FAILED','REFUNDED')), consumer(anyOf('PENDING','COMPLETED','FAILED','REFUNDED'))),
            amount:        $(producer(anyPositiveInt()), consumer(anyPositiveInt())),
            createdAt:     $(producer(anyIso8601WithOffset()), consumer(anyIso8601WithOffset()))
        )
    }
}
