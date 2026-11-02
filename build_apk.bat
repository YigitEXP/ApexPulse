@echo off
chcp 65001 >NUL
echo ========================================================
echo   ApexPulse Telemetry - Otomatik APK Derleme Araci
echo ========================================================
echo.

call gradlew.bat assembleDebug
if %ERRORLEVEL% equ 0 (
    echo.
    echo ========================================================
    echo [BASARILI] APK dosyaniz hazir:
    echo %~dp0app\build\outputs\apk\debug\app-debug.apk
    echo ========================================================
)
pause
