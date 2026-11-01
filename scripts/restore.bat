@echo off
rem Restores the assistant / speech-recognition / accessibility settings to their values from before the install.
rem Usage: scripts\restore.bat   |   scripts\restore.bat auto
setlocal EnableExtensions
cd /d "%~dp0.."
if not exist logs mkdir logs
set "LOG=logs\restore.log"
> "%LOG%" echo [%date% %time%] restore
if not exist scripts\tv.txt goto notv
set /p TV=<scripts\tv.txt
set "ADB="
where adb >nul 2>&1 && set "ADB=adb"
if not defined ADB if defined ANDROID_HOME if exist "%ANDROID_HOME%\platform-tools\adb.exe" set "ADB=%ANDROID_HOME%\platform-tools\adb.exe"
if not defined ADB if exist "%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe" set "ADB=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe"
if not defined ADB goto noadb
"%ADB%" connect %TV% >> "%LOG%" 2>&1
"%ADB%" -s %TV% push scripts\restore-defaults.sh /data/local/tmp/vt-restore.sh >> "%LOG%" 2>&1
"%ADB%" -s %TV% shell sh /data/local/tmp/vt-restore.sh >> "%LOG%" 2>&1
echo Restore done. Details: %LOG%
goto end
:notv
echo scripts\tv.txt not found.
goto end
:noadb
echo adb not found.
:end
if /i "%~1"=="auto" exit /b 0
pause
