@echo off
echo ========================================
echo Starting User Service (Port 8081)
echo ========================================
cd /d C:\project\livemart
call gradlew.bat :user-service:bootRun
