# Saga Pattern Test (Without Stock Reset)
$productId = 4

Write-Host "`n=== Step 1: Check Current Stock ===" -ForegroundColor Cyan
$product = Invoke-RestMethod -Uri "http://localhost:8082/api/products/$productId" -Method Get
$initialStock = $product.stockQuantity
Write-Host "Current Stock: $initialStock units" -ForegroundColor Yellow
Write-Host "Product: $($product.name)" -ForegroundColor White

if ($initialStock -lt 2) {
    Write-Host "`nERROR: Not enough stock! Need at least 2 units." -ForegroundColor Red
    Write-Host "Please manually set stock to 10 and retry." -ForegroundColor Yellow
    Read-Host "Press Enter to exit"
    exit
}

Write-Host "`n=== Step 2: Create Order (Buy 2 units) ===" -ForegroundColor Cyan
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

Write-Host "`n=== Step 3: Check Stock After Order ===" -ForegroundColor Cyan
$afterOrderStock = (Invoke-RestMethod -Uri "http://localhost:8082/api/products/$productId" -Method Get).stockQuantity
Write-Host "Stock before order: $initialStock units" -ForegroundColor White
Write-Host "Stock after order: $afterOrderStock units" -ForegroundColor Yellow
$expectedStock = $initialStock - 2
if ($afterOrderStock -eq $expectedStock) {
    Write-Host "SUCCESS: Stock deducted correctly ($initialStock -> $afterOrderStock)" -ForegroundColor Green
} else {
    Write-Host "WARNING: Expected $expectedStock, Got $afterOrderStock" -ForegroundColor Red
}

Write-Host "`n=== Step 4: Cancel Order ===" -ForegroundColor Cyan
try {
    $cancelResponse = Invoke-RestMethod -Uri "http://localhost:8083/api/orders/$orderId/cancel?reason=CustomerRequest" -Method Post
    Write-Host "Order cancelled successfully!" -ForegroundColor Green
    Write-Host "Cancel reason: CustomerRequest" -ForegroundColor Yellow
} catch {
    Write-Host "Order cancellation failed: $_" -ForegroundColor Red
    Write-Host "Error details: $($_.Exception.Message)" -ForegroundColor Red
    Read-Host "Press Enter to exit"
    exit
}

Write-Host "`nWaiting for Kafka event processing..." -ForegroundColor Yellow
Start-Sleep -Seconds 3

Write-Host "`n=== Step 5: Check Stock After Cancellation ===" -ForegroundColor Cyan
$finalStock = (Invoke-RestMethod -Uri "http://localhost:8082/api/products/$productId" -Method Get).stockQuantity
Write-Host "Stock before order: $initialStock units" -ForegroundColor White
Write-Host "Stock after order: $afterOrderStock units" -ForegroundColor White
Write-Host "Stock after cancel: $finalStock units" -ForegroundColor Yellow

Write-Host "`n=== Final Result ===" -ForegroundColor Cyan
if ($finalStock -eq $initialStock) {
    Write-Host "`n========================================" -ForegroundColor Green
    Write-Host "SUCCESS: Stock restored correctly!" -ForegroundColor Green
    Write-Host "  Before: $initialStock units" -ForegroundColor Green
    Write-Host "  After Cancel: $finalStock units" -ForegroundColor Green
    Write-Host "SUCCESS: Saga Pattern is working!" -ForegroundColor Green
    Write-Host "========================================" -ForegroundColor Green
} else {
    Write-Host "`n========================================" -ForegroundColor Red
    Write-Host "FAILED: Stock not restored!" -ForegroundColor Red
    Write-Host "  Expected: $initialStock units" -ForegroundColor Red
    Write-Host "  Got: $finalStock units" -ForegroundColor Red
    Write-Host "========================================" -ForegroundColor Red
}

Write-Host "`n=== Check Logs ===" -ForegroundColor Cyan
Write-Host "Order Service should show:" -ForegroundColor Yellow
Write-Host "  - ORDER_CREATED event published" -ForegroundColor White
Write-Host "  - ORDER_CANCELLED event published" -ForegroundColor White
Write-Host "`nProduct Service should show:" -ForegroundColor Yellow
Write-Host "  - ORDER_CANCELLED event received" -ForegroundColor White
Write-Host "  - Stock restored: productId=$productId, quantity=2" -ForegroundColor White

Write-Host "`n" -NoNewline
Read-Host "Press Enter to exit"