# Saga Pattern Test - Complete Version
$productId = 4

Write-Host "`n=== Step 1: Reset Stock to 10 ===" -ForegroundColor Cyan
try {
    $body = @{ stockQuantity = 10 } | ConvertTo-Json
    Invoke-RestMethod -Uri "http://localhost:8082/api/products/$productId/stock" `
        -Method Patch `
        -Body $body `
        -ContentType "application/json"
    Write-Host "Stock reset to 10 units" -ForegroundColor Green
} catch {
    Write-Host "Failed to reset stock: $_" -ForegroundColor Red
    Read-Host "Press Enter to exit"
    exit
}

Start-Sleep -Seconds 1

Write-Host "`n=== Step 2: Check Initial Stock ===" -ForegroundColor Cyan
$product = Invoke-RestMethod -Uri "http://localhost:8082/api/products/$productId" -Method Get
$initialStock = $product.stockQuantity
Write-Host "Product: $($product.name)" -ForegroundColor White
Write-Host "Initial Stock: $initialStock units" -ForegroundColor Yellow

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
    $orderResponse = Invoke-RestMethod -Uri "http://localhost:8083/api/orders" `
        -Method Post `
        -Body $orderRequest `
        -ContentType "application/json"
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
    Write-Host "WARNING: Expected 8, Got $afterOrderStock" -ForegroundColor Red
}

Write-Host "`n=== Step 5: Cancel Order ===" -ForegroundColor Cyan
try {
    $cancelResponse = Invoke-RestMethod -Uri "http://localhost:8083/api/orders/$orderId/cancel?reason=CustomerRequest" -Method Post
    Write-Host "Order cancelled successfully!" -ForegroundColor Green
} catch {
    Write-Host "Order cancellation failed: $_" -ForegroundColor Red
    Write-Host "Error: $($_.Exception.Message)" -ForegroundColor Red
    Read-Host "Press Enter to exit"
    exit
}

Write-Host "`nWaiting for Kafka event processing..." -ForegroundColor Yellow
Start-Sleep -Seconds 3

Write-Host "`n=== Step 6: Check Stock After Cancellation ===" -ForegroundColor Cyan
$finalStock = (Invoke-RestMethod -Uri "http://localhost:8082/api/products/$productId" -Method Get).stockQuantity
Write-Host "Stock after cancellation: $finalStock units" -ForegroundColor Yellow

Write-Host "`n========================================" -ForegroundColor Cyan
Write-Host "           FINAL RESULT                  " -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Initial Stock:    $initialStock units" -ForegroundColor White
Write-Host "After Order:      $afterOrderStock units" -ForegroundColor White
Write-Host "After Cancel:     $finalStock units" -ForegroundColor White
Write-Host "========================================" -ForegroundColor Cyan

if ($finalStock -eq $initialStock) {
    Write-Host "`n" -NoNewline
    Write-Host "  SUCCESS  " -BackgroundColor Green -ForegroundColor Black
    Write-Host "`nStock restored correctly!" -ForegroundColor Green
    Write-Host "Saga Pattern is working perfectly!" -ForegroundColor Green
    Write-Host "`n========================================" -ForegroundColor Green
} else {
    Write-Host "`n" -NoNewline
    Write-Host "  FAILED  " -BackgroundColor Red -ForegroundColor White
    Write-Host "`nStock NOT restored!" -ForegroundColor Red
    Write-Host "Expected: $initialStock, Got: $finalStock" -ForegroundColor Red
    Write-Host "`n========================================" -ForegroundColor Red
}

Write-Host "`n=== Check Service Logs ===" -ForegroundColor Cyan
Write-Host "`nOrder Service should show:" -ForegroundColor Yellow
Write-Host "  [V] ORDER_CREATED event published" -ForegroundColor White
Write-Host "  [V] Payment cancelled" -ForegroundColor White
Write-Host "  [V] ORDER_CANCELLED event published" -ForegroundColor White
Write-Host "`nProduct Service should show:" -ForegroundColor Yellow
Write-Host "  [V] ORDER_CANCELLED event received" -ForegroundColor White
Write-Host "  [V] Stock restored: productId=$productId, quantity=2" -ForegroundColor White
Write-Host "  [V] Order cancellation processed" -ForegroundColor White

Write-Host "`n" -NoNewline
Read-Host "Press Enter to exit"