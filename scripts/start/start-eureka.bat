@echo off
echo ========================================
echo Starting Eureka Server (Port 8761)
echo ========================================
cd /d C:\project\livemart
call gradlew.bat :eureka-server:bootRun
