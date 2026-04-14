@echo off
echo ========================================
echo Starting Analytics Service (Port 8087)
echo ========================================
cd /d C:\project\livemart
call gradlew.bat :analytics-service:bootRun
