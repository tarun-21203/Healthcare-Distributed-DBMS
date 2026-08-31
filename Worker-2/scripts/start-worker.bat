@echo off
echo Starting Distributed Database Worker...
cd /d "%~dp0.."

set WORKER_ID=worker-1
set PORT=8081

if not "%1"=="" set WORKER_ID=%1
if not "%2"=="" set PORT=%2

call gradlew.bat build
if %ERRORLEVEL% EQU 0 (
    java -jar build\libs\Worker-1.0-SNAPSHOT.jar %WORKER_ID% %PORT%
) else (
    echo Build failed!
    pause
)
