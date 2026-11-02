@echo off
setlocal EnableDelayedExpansion
cd /d "%~dp0"
set FB=npx -y firebase-tools@latest
set PROJECT_ID=apexpulse-3039-8eacf
set PKG=com.wheelcharm.apexpulse
set LOCATION=eur3
echo ====================================================
echo   ApexPulse - Otomatik Firebase Deploy (%PROJECT_ID%)
echo ====================================================
echo [1/6] Google oturumu kontrol ediliyor...
call %FB% login:list | findstr /i "@" >NUL
if errorlevel 1 call %FB% login
call %FB% login:list | findstr /i "@" >NUL
if errorlevel 1 goto :login_fail
echo [2/6] Proje seciliyor: %PROJECT_ID%
call %FB% use %PROJECT_ID% --add --alias default
if errorlevel 1 goto :project_fail
echo [3/6] Android uygulamasi kaydediliyor...
call %FB% apps:create ANDROID "ApexPulse Telemetry" --package-name %PKG% --project %PROJECT_ID%
echo [4/6] google-services.json indiriliyor...
call %FB% apps:sdkconfig ANDROID --project %PROJECT_ID% -o app\google-services.json
if not exist app\google-services.json goto :sdk_fail
echo [5/6] Firestore olusturuluyor ve kurallar yukleniyor...
call %FB% firestore:databases:create "(default)" --location=%LOCATION% --project %PROJECT_ID%
call %FB% deploy --only firestore --project %PROJECT_ID%
if errorlevel 1 goto :rules_fail
echo [6/6] Firebase bagli APK derleniyor...
call gradlew.bat assembleDebug
if errorlevel 1 goto :build_fail
echo.
echo ====================================================
echo [BASARIYLA TAMAMLANDI]
echo Console   : https://console.firebase.google.com/project/%PROJECT_ID%/overview
echo Firestore : https://console.firebase.google.com/project/%PROJECT_ID%/firestore
echo APK       : %~dp0app\build\outputs\apk\debug\app-debug.apk
echo ====================================================
echo ONEMLI: Authentication - Sign-in method - Anonymous secenegini ETKINLESTIRIN.
start https://console.firebase.google.com/project/%PROJECT_ID%/authentication/providers
explorer "%~dp0app\build\outputs\apk\debug"
if "%1"=="" pause
exit /b 0
:login_fail
echo [HATA] Giris yapilamadi.
if "%1"=="" pause
exit /b 1
:project_fail
echo [HATA] %PROJECT_ID% projesine erisilemedi.
if "%1"=="" pause
exit /b 1
:sdk_fail
echo [HATA] google-services.json indirilemedi.
if "%1"=="" pause
exit /b 1
:rules_fail
echo [HATA] Firestore kurallari yuklenemedi.
if "%1"=="" pause
exit /b 1
:build_fail
echo [HATA] APK derlemesi basarisiz.
if "%1"=="" pause
exit /b 1
