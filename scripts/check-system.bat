@echo off
chcp 65001 >nul
echo ========================================
echo LiveMart 시스템 상태 확인
echo ========================================

echo.
echo [Docker 컨테이너 상태]
echo ----------------------------------------
docker ps --filter "name=livemart-" --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"

echo.
echo [MySQL 연결 테스트]
echo ----------------------------------------
echo User DB:
docker exec livemart-mysql-user mysql -uroot -proot123 -e "SELECT 'OK' as Status;" 2>nul
echo Product DB:
docker exec livemart-mysql-product mysql -uroot -proot123 -e "SELECT 'OK' as Status;" 2>nul
echo Order DB:
docker exec livemart-mysql-order mysql -uroot -proot123 -e "SELECT 'OK' as Status;" 2>nul

echo.
echo [포트 사용 현황]
echo ----------------------------------------
netstat -ano | findstr "8761 8080 8081 8082 8083"

echo.
echo [Eureka Server 확인]
echo ----------------------------------------
curl -s http://localhost:8761/actuator/health 2>nul || echo Eureka가 실행되지 않았습니다.

echo.
echo ========================================
echo 시스템 상태 확인 완료
echo ========================================
pause
