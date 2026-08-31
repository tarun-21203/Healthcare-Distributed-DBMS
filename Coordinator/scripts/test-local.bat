@echo off
echo ========================================
echo Local Distributed Database Test Setup
echo ========================================
echo.
echo This script will help you test the distributed setup locally.
echo You need to run workers in separate terminals first.
echo.
echo Step 1: Open Terminal 1 and run:
echo   cd Worker
echo   gradlew.bat run --args="worker-1 8081"
echo.
echo Step 2: Open Terminal 2 and run:
echo   cd Worker
echo   gradlew.bat run --args="worker-2 8082"
echo.
echo Step 3: Press any key here to start Coordinator...
pause

echo.
echo Creating local workers.config...
(
echo # Local testing configuration
echo worker-1,localhost,8081
echo worker-2,localhost,8082
) > workers.config

echo.
echo Starting Coordinator...
call gradlew.bat build
if %ERRORLEVEL% EQU 0 (
    java -jar build\libs\Coordinator-1.0-SNAPSHOT.jar
) else (
    echo Build failed!
    pause
)
