@echo off
rem Sadece asistan/tanima/erisilebilirlik atamasini yeniden uygular (APK kurmaz). Ayarlar geri alinirsa bunu calistir.
setlocal EnableExtensions
cd /d "%~dp0.."
if not exist logs mkdir logs
set "LOG=logs\setup.log"
> "%LOG%" echo [%date% %time%] setup
if not exist scripts\tv.txt goto notv
set /p TV=<scripts\tv.txt
set "ADB="
where adb >nul 2>&1 && set "ADB=adb"
if not defined ADB if defined ANDROID_HOME if exist "%ANDROID_HOME%\platform-tools\adb.exe" set "ADB=%ANDROID_HOME%\platform-tools\adb.exe"
if not defined ADB if exist "%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe" set "ADB=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe"
if not defined ADB goto noadb
"%ADB%" connect %TV% >> "%LOG%" 2>&1
"%ADB%" -s %TV% push scripts\tv-setup.sh /data/local/tmp/vt-setup.sh >> "%LOG%" 2>&1
"%ADB%" -s %TV% shell sh /data/local/tmp/vt-setup.sh >> "%LOG%" 2>&1
echo Tamam. Ayrinti: %LOG%
goto end
:notv
echo scripts\tv.txt yok.
goto end
:noadb
echo adb bulunamadi.
:end
if /i "%~1"=="auto" exit /b 0
pause
