@echo off
echo Starting Distributed Database Coordinator...
cd /d "%~dp0.."
call gradlew.bat build
if %ERRORLEVEL% EQU 0 (
    java -jar build\libs\Coordinator-1.0-SNAPSHOT.jar
) else (
    echo Build failed!
    pause
)
