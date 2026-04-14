# Saga Pattern (Compensation Transaction) Test Script
$productId = 4

Write-Host "`n=== Step 1: Check Initial Stock ===" -ForegroundColor Cyan
$initialStock = (Invoke-RestMethod -Uri "http://localhost:8082/api/products/$productId" -Method Get).stockQuantity
Write-Host "Initial Stock: $initialStock units" -ForegroundColor Yellow

Write-Host "`n=== Step 2: Reset Stock to 10 ===" -ForegroundColor Cyan
Invoke-RestMethod -Uri "http://localhost:8082/api/products/$productId/stock?quantity=10" -Method PATCH
Write-Host "Stock reset completed: 10 units" -ForegroundColor Green

Write-Host "`n=== Step 3: Create Order (Buy 2 units) ===" -ForegroundColor Cyan
$orderRequest = @{
    userId = 1
    items = @(
        @{
            productId = $productId
            quantity = 2
        }
    )
    deliveryAddress = "Seoul Gangnam"
    phoneNumber = "01012345678"
    orderNote = "Fast delivery please"
    paymentMethod = "CREDIT_CARD"
} | ConvertTo-Json -Depth 10

try {
    $orderResponse = Invoke-RestMethod -Uri "http://localhost:8083/api/orders" -Method Post -Body $orderRequest -ContentType "application/json"
    $orderId = $orderResponse.id
    $orderNumber = $orderResponse.orderNumber
    Write-Host "Order created successfully!" -ForegroundColor Green
    Write-Host "Order ID: $orderId" -ForegroundColor Yellow
    Write-Host "Order Number: $orderNumber" -ForegroundColor Yellow
} catch {
    Write-Host "Order creation failed: $_" -ForegroundColor Red
    Read-Host "Press Enter to exit"
    exit
}

Start-Sleep -Seconds 2

Write-Host "`n=== Step 4: Check Stock After Order ===" -ForegroundColor Cyan
$afterOrderStock = (Invoke-RestMethod -Uri "http://localhost:8082/api/products/$productId" -Method Get).stockQuantity
Write-Host "Stock after order: $afterOrderStock units" -ForegroundColor Yellow
if ($afterOrderStock -eq 8) {
    Write-Host "SUCCESS: Stock deducted correctly (10 -> 8)" -ForegroundColor Green
} else {
    Write-Host "FAILED: Expected 8, Got $afterOrderStock" -ForegroundColor Red
}

Write-Host "`n=== Step 5: Cancel Order ===" -ForegroundColor Cyan
try {
    $cancelResponse = Invoke-RestMethod -Uri "http://localhost:8083/api/orders/$orderId/cancel?reason=CustomerRequest" -Method Post
    Write-Host "Order cancelled successfully!" -ForegroundColor Green
    Write-Host "Cancel reason: CustomerRequest" -ForegroundColor Yellow
} catch {
    Write-Host "Order cancellation failed: $_" -ForegroundColor Red
    Read-Host "Press Enter to exit"
    exit
}

Start-Sleep -Seconds 3

Write-Host "`n=== Step 6: Check Stock After Cancellation ===" -ForegroundColor Cyan
$finalStock = (Invoke-RestMethod -Uri "http://localhost:8082/api/products/$productId" -Method Get).stockQuantity
Write-Host "Stock after cancellation: $finalStock units" -ForegroundColor Yellow

Write-Host "`n=== Step 7: Final Result ===" -ForegroundColor Cyan
Write-Host "Initial Stock: 10 units" -ForegroundColor White
Write-Host "After Order: $afterOrderStock units" -ForegroundColor White
Write-Host "After Cancel: $finalStock units" -ForegroundColor White

if ($finalStock -eq 10) {
    Write-Host "`n========================================" -ForegroundColor Green
    Write-Host "SUCCESS: Stock restored correctly!" -ForegroundColor Green
    Write-Host "SUCCESS: Saga Pattern is working!" -ForegroundColor Green
    Write-Host "========================================" -ForegroundColor Green
} else {
    Write-Host "`n========================================" -ForegroundColor Red
    Write-Host "FAILED: Stock not restored!" -ForegroundColor Red
    Write-Host "Expected: 10, Got: $finalStock" -ForegroundColor Red
    Write-Host "========================================" -ForegroundColor Red
}

Write-Host "`n=== Kafka Event Logs ===" -ForegroundColor Cyan
Write-Host "Check Order Service logs for:" -ForegroundColor Yellow
Write-Host "  - ORDER_CREATED event published" -ForegroundColor White
Write-Host "  - ORDER_CANCELLED event published" -ForegroundColor White
Write-Host "`nCheck Product Service logs for:" -ForegroundColor Yellow
Write-Host "  - ORDER_CANCELLED event received" -ForegroundColor White
Write-Host "  - Stock restored log" -ForegroundColor White

Write-Host "`n" -NoNewline
Read-Host "Press Enter to exit"