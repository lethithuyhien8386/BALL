@echo off
cd /d "%~dp0"
where gradle >nul 2>&1
if errorlevel 1 (
  echo Chua co Gradle tren PATH. Cai Gradle 8.9 va JDK 17, SDK API35 hoac mo du an bang Android Studio.
  exit /b 1
)
gradle assembleDebug
if errorlevel 1 exit /b 1
echo APK: app\build\outputs\apk\debug\app-debug.apk
