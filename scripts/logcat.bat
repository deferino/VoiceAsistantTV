@echo off
rem TV'nin gunlugunu logs\logcat.log dosyasina yazar.
rem Once "scripts\logcat.bat clear" ile temizle, TV'de asistan tusuna bas, sonra "scripts\logcat.bat" ile al.
rem Ekleme: "auto" verirsen beklemeden kapanir.
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
if /i "%~1"=="clear" goto doclear
"%ADB%" -s %TV% logcat -d -v threadtime > logs\logcat.log 2>&1
echo Gunluk yazildi: logs\logcat.log
goto end
:doclear
"%ADB%" -s %TV% logcat -c
echo Gunluk temizlendi. Simdi TV'de asistan tusuna bas.
goto end
:notv
echo scripts\tv.txt yok.
goto end
:noadb
echo adb bulunamadi.
:end
if /i "%~2"=="auto" exit /b 0
if /i "%~1"=="auto" exit /b 0
pause
