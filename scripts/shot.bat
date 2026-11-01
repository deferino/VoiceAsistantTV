@echo off
rem Takes a screenshot of the TV -> logs\screen.png
rem Usage: run it, immediately press the assistant key and speak; the screenshot is taken at 25 seconds.
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
echo Now press the assistant key on the TV, say something and wait for the results to appear.
echo The screenshot will be taken automatically in 25 seconds...
ping -n 26 127.0.0.1 >nul
"%ADB%" -s %TV% shell screencap -p /data/local/tmp/vt-screen.png
"%ADB%" -s %TV% pull /data/local/tmp/vt-screen.png logs\screen.png
"%ADB%" -s %TV% shell rm -f /data/local/tmp/vt-screen.png
echo Done: logs\screen.png
goto end
:notv
echo scripts\tv.txt not found.
goto end
:noadb
echo adb not found.
:end
if /i "%~1"=="auto" exit /b 0
pause
