# 보상 트랜잭션 테스트 스크립트
$productId = 4

Write-Host "`n=== 1. 초기 재고 확인 ===" -ForegroundColor Cyan
$initialStock = (Invoke-RestMethod -Uri "http://localhost:8082/api/products/$productId" -Method Get).stockQuantity
Write-Host "초기 재고: $initialStock 개" -ForegroundColor Yellow

Write-Host "`n=== 2. 재고 10개로 초기화 ===" -ForegroundColor Cyan
Invoke-RestMethod -Uri "http://localhost:8082/api/products/$productId/stock?quantity=10" -Method PATCH
Write-Host "재고 초기화 완료: 10개" -ForegroundColor Green

Write-Host "`n=== 3. 주문 생성 (2개 구매) ===" -ForegroundColor Cyan
$orderRequest = @{
    userId = 1
    items = @(
        @{
            productId = $productId
            quantity = 2
        }
    )
    deliveryAddress = "서울시 강남구"
    phoneNumber = "010-1234-5678"
    orderNote = "빠른 배송 부탁드립니다"
    paymentMethod = "CREDIT_CARD"
} | ConvertTo-Json -Depth 10

try {
    $orderResponse = Invoke-RestMethod -Uri "http://localhost:8083/api/orders" -Method Post -Body $orderRequest -ContentType "application/json"
    $orderId = $orderResponse.id
    $orderNumber = $orderResponse.orderNumber
    Write-Host "주문 생성 성공!" -ForegroundColor Green
    Write-Host "주문 ID: $orderId" -ForegroundColor Yellow
    Write-Host "주문 번호: $orderNumber" -ForegroundColor Yellow
} catch {
    Write-Host "주문 생성 실패: $_" -ForegroundColor Red
    Read-Host "Press Enter to exit"
    exit
}

Start-Sleep -Seconds 2

Write-Host "`n=== 4. 재고 확인 (차감 확인) ===" -ForegroundColor Cyan
$afterOrderStock = (Invoke-RestMethod -Uri "http://localhost:8082/api/products/$productId" -Method Get).stockQuantity
Write-Host "주문 후 재고: $afterOrderStock 개" -ForegroundColor Yellow
if ($afterOrderStock -eq 8) {
    Write-Host "✅ 재고 차감 성공! (10 → 8)" -ForegroundColor Green
} else {
    Write-Host "❌ 재고 차감 실패! 예상: 8개, 실제: $afterOrderStock 개" -ForegroundColor Red
}

Write-Host "`n=== 5. 주문 취소 ===" -ForegroundColor Cyan
try {
    $cancelResponse = Invoke-RestMethod -Uri "http://localhost:8083/api/orders/$orderId/cancel?reason=고객요청" -Method Post
    Write-Host "주문 취소 성공!" -ForegroundColor Green
    Write-Host "취소 사유: 고객요청" -ForegroundColor Yellow
} catch {
    Write-Host "주문 취소 실패: $_" -ForegroundColor Red
    Read-Host "Press Enter to exit"
    exit
}

Start-Sleep -Seconds 3

Write-Host "`n=== 6. 재고 확인 (복구 확인) ===" -ForegroundColor Cyan
$finalStock = (Invoke-RestMethod -Uri "http://localhost:8082/api/products/$productId" -Method Get).stockQuantity
Write-Host "취소 후 재고: $finalStock 개" -ForegroundColor Yellow

Write-Host "`n=== 7. 최종 결과 ===" -ForegroundColor Cyan
Write-Host "초기 재고: 10개" -ForegroundColor White
Write-Host "주문 후 재고: $afterOrderStock 개" -ForegroundColor White
Write-Host "취소 후 재고: $finalStock 개" -ForegroundColor White

if ($finalStock -eq 10) {
    Write-Host "`n✅ 테스트 성공! 재고가 정상적으로 복구되었습니다!" -ForegroundColor Green
    Write-Host "✅ 보상 트랜잭션(Saga Pattern)이 정상 작동합니다!" -ForegroundColor Green
} else {
    Write-Host "`n❌ 테스트 실패! 재고가 복구되지 않았습니다!" -ForegroundColor Red
    Write-Host "예상: 10개, 실제: $finalStock 개" -ForegroundColor Red
}

Write-Host "`n=== Kafka 이벤트 로그 확인 ===" -ForegroundColor Cyan
Write-Host "Order Service 로그를 확인하세요:" -ForegroundColor Yellow
Write-Host "  - ORDER_CREATED 이벤트 발행" -ForegroundColor White
Write-Host "  - ORDER_CANCELLED 이벤트 발행" -ForegroundColor White
Write-Host "`nProduct Service 로그를 확인하세요:" -ForegroundColor Yellow
Write-Host "  - ORDER_CANCELLED 이벤트 수신" -ForegroundColor White
Write-Host "  - Stock restored 로그" -ForegroundColor White

Write-Host "`n" -NoNewline
Read-Host "Press Enter to exit"