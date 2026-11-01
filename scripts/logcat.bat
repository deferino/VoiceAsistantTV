@echo off
rem Writes the TV's log to logs\logcat.log.
rem First clear it with "scripts\logcat.bat clear", press the assistant key on the TV, then fetch it with "scripts\logcat.bat".
rem Extra: with "auto" it closes without waiting.
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
echo Log written: logs\logcat.log
goto end
:doclear
"%ADB%" -s %TV% logcat -c
echo Log cleared. Now press the assistant key on the TV.
goto end
:notv
echo scripts\tv.txt not found.
goto end
:noadb
echo adb not found.
:end
if /i "%~2"=="auto" exit /b 0
if /i "%~1"=="auto" exit /b 0
pause
