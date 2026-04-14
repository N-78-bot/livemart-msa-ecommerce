@echo off
echo ========================================
echo Starting Order Service (Port 8083)
echo ========================================
cd /d C:\project\livemart
call gradlew.bat :order-service:bootRun
