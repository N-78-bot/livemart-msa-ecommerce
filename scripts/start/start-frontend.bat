@echo off
echo ========================================
echo LiveMart Frontend Starting...
echo ========================================

cd frontend

echo Installing dependencies (if needed)...
call npm install

echo.
echo Starting Next.js dev server...
echo Frontend will be available at http://localhost:3000
echo.
echo Press Ctrl+C to stop
echo.

call npm run dev

pause
