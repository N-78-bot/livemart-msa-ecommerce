@echo off
REM LiveMart Database Initialization Script for Windows

echo ========================================
echo LiveMart Database Initialization
echo ========================================
echo.
echo This script will create the following databases:
echo - userdb
echo - productdb
echo - orderdb
echo.
echo Please ensure MySQL is running on localhost:3306
echo with root user (password: root)
echo.
pause

REM Try common MySQL installation paths
set MYSQL_PATH=""

if exist "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe" (
    set MYSQL_PATH="C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe"
) else if exist "C:\Program Files\MySQL\MySQL Server 8.4\bin\mysql.exe" (
    set MYSQL_PATH="C:\Program Files\MySQL\MySQL Server 8.4\bin\mysql.exe"
) else if exist "C:\xampp\mysql\bin\mysql.exe" (
    set MYSQL_PATH="C:\xampp\mysql\bin\mysql.exe"
) else (
    echo MySQL executable not found in common locations.
    echo Please execute this command manually:
    echo mysql -u root -proot ^< init-databases.sql
    pause
    exit /b 1
)

echo.
echo Found MySQL at: %MYSQL_PATH%
echo.
echo Creating databases...

%MYSQL_PATH% -u root -proot < init-databases.sql

if %ERRORLEVEL% EQU 0 (
    echo.
    echo ========================================
    echo SUCCESS: All databases created!
    echo ========================================
) else (
    echo.
    echo ========================================
    echo ERROR: Failed to create databases
    echo ========================================
    echo.
    echo Please check:
    echo 1. MySQL is running
    echo 2. Root password is 'root'
    echo 3. Run manually: mysql -u root -proot ^< init-databases.sql
)

echo.
pause
