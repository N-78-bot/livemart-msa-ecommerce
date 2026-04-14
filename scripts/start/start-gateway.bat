@echo off
echo ========================================
echo Starting API Gateway (Port 8080)
echo ========================================
cd /d C:\project\livemart
call gradlew.bat :api-gateway:bootRun
