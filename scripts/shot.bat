@echo off
rem TV ekraninin goruntusunu alir -> logs\screen.png
rem Kullanim: calistir, hemen asistan tusuna bas ve konus; 25. saniyede ekran goruntusu alinir.
setlocal EnableExtensions
cd /d "%~dp0.."
if not exist logs mkdir logs
if not exist scripts\tv.txt goto notv
set /p TV=<scripts\tv.txt
set "ADB="
where adb >nul 2>&1 && set "ADB=adb"
if not defined ADB if defined ANDROID_HOME if exist "%ANDROID_HOME%\platform-tools\adb.exe" set "ADB=%ANDROID_HOME%\platform-tools\adb.exe"
if not defined ADB if exist "%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe" set "ADB=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe"
if not defined ADB goto noadb
"%ADB%" connect %TV% >nul 2>&1
echo Simdi TV'de asistan tusuna bas, bir sey soyle ve sonuclar ekrana gelsin.
echo Ekran goruntusu 25 saniye sonra otomatik alinacak...
ping -n 26 127.0.0.1 >nul
"%ADB%" -s %TV% shell screencap -p /data/local/tmp/vt-screen.png
"%ADB%" -s %TV% pull /data/local/tmp/vt-screen.png logs\screen.png
"%ADB%" -s %TV% shell rm -f /data/local/tmp/vt-screen.png
echo Bitti: logs\screen.png
goto end
:notv
echo scripts\tv.txt yok.
goto end
:noadb
echo adb bulunamadi.
:end
if /i "%~1"=="auto" exit /b 0
pause
