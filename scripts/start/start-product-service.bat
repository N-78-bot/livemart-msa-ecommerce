@echo off
echo ========================================
echo Starting Product Service (Port 8082)
echo ========================================
cd /d C:\project\livemart
call gradlew.bat :product-service:bootRun
