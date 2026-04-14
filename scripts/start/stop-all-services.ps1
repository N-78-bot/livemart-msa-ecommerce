# LiveMart - 모든 서비스 중지 스크립트

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "LiveMart 서비스 중지 중..." -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# 포트별 PID 찾기 및 종료
$ports = @(8761, 8081, 8082, 8083, 8084, 8086)
$serviceNames = @{
    8761 = "Eureka Server"
    8081 = "User Service"
    8082 = "Product Service"
    8083 = "Order Service"
    8084 = "Payment Service"
    8086 = "Notification Service"
}

foreach ($port in $ports) {
    $serviceName = $serviceNames[$port]

    # netstat으로 포트 사용 중인 PID 찾기
    $netstatOutput = netstat -ano | Select-String ":$port\s" | Select-String "LISTENING"

    if ($netstatOutput) {
        # PID 추출 (마지막 컬럼)
        $line = $netstatOutput.Line -split '\s+' | Where-Object { $_ -ne '' }
        $pid = $line[-1]

        if ($pid -and $pid -match '^\d+$') {
            Write-Host "[$serviceName] 중지 중... (PID: $pid, Port: $port)" -ForegroundColor Yellow

            try {
                Stop-Process -Id $pid -Force -ErrorAction Stop
                Write-Host "  ✓ 중지 완료" -ForegroundColor Green
            }
            catch {
                Write-Host "  ✗ 중지 실패: $_" -ForegroundColor Red
            }
        }
    }
    else {
        Write-Host "[$serviceName] 실행 중이지 않음 (Port: $port)" -ForegroundColor Gray
    }
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "모든 서비스 중지 완료!" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "다음 순서로 서비스를 시작하세요:" -ForegroundColor Yellow
Write-Host "  1. Eureka Server (8761)" -ForegroundColor White
Write-Host "  2. 다른 서비스들 (User, Product, Order, Payment, Notification)" -ForegroundColor White
Write-Host "  3. API Gateway (8080)" -ForegroundColor White
Write-Host ""
