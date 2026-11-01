@echo off
rem Writes the raw key events from the remote to logs\keys.log for 15 seconds.
rem Run it, then immediately press the assistant/microphone key and a few other keys.
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
echo Now press keys on the remote for 15 seconds (especially the assistant/microphone key)...
"%ADB%" -s %TV% shell timeout 15 getevent -lt > logs\keys.log 2>&1
echo Done: logs\keys.log
goto end
:notv
echo scripts\tv.txt not found.
goto end
:noadb
echo adb not found.
:end
if /i "%~1"=="auto" exit /b 0
pause
