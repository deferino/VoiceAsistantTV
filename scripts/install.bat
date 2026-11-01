@echo off
rem Installs the APK on the TV and binds the assistant key / services to this app.
rem First write the TV's address:port into scripts\tv.txt (example: 192.168.1.50:5555).
rem Optional: put config.json (API key) in the project root; it is transferred to the TV automatically.
rem Usage: scripts\install.bat   |   scripts\install.bat auto
setlocal EnableExtensions
cd /d "%~dp0.."
if not exist logs mkdir logs
set "LOG=logs\install.log"
set "PKG=com.voiceasistanttv.stt"
set "APK=app\build\outputs\apk\debug\app-debug.apk"
> "%LOG%" echo [%date% %time%] install starting

if not exist "%APK%" goto noapk
if not exist scripts\tv.txt goto notv
set /p TV=<scripts\tv.txt

set "ADB="
where adb >nul 2>&1 && set "ADB=adb"
if not defined ADB if defined ANDROID_HOME if exist "%ANDROID_HOME%\platform-tools\adb.exe" set "ADB=%ANDROID_HOME%\platform-tools\adb.exe"
if not defined ADB if exist "%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe" set "ADB=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe"
if not defined ADB goto noadb

>> "%LOG%" echo == adb connect %TV%
"%ADB%" connect %TV% >> "%LOG%" 2>&1
"%ADB%" -s %TV% get-state >> "%LOG%" 2>&1
if errorlevel 1 goto notconnected

>> "%LOG%" echo == install
"%ADB%" -s %TV% install -r -g "%APK%" >> "%LOG%" 2>&1
if errorlevel 1 goto installfail

if not exist config.json goto nocfg
>> "%LOG%" echo == transferring config.json
"%ADB%" -s %TV% push config.json /data/local/tmp/vt-config.json >> "%LOG%" 2>&1
"%ADB%" -s %TV% shell chmod 644 /data/local/tmp/vt-config.json >> "%LOG%" 2>&1
"%ADB%" -s %TV% shell run-as %PKG% mkdir -p files >> "%LOG%" 2>&1
"%ADB%" -s %TV% shell "run-as %PKG% cp /data/local/tmp/vt-config.json files/config.json" >> "%LOG%" 2>&1
"%ADB%" -s %TV% shell rm -f /data/local/tmp/vt-config.json >> "%LOG%" 2>&1
:nocfg

>> "%LOG%" echo == tv-setup.sh
"%ADB%" -s %TV% push scripts\tv-setup.sh /data/local/tmp/vt-setup.sh >> "%LOG%" 2>&1
"%ADB%" -s %TV% shell sh /data/local/tmp/vt-setup.sh >> "%LOG%" 2>&1

rem NOTE: do NOT use "am force-stop" here. If the assistant/recognition service is stopped, Android reverts the settings to Google.
>> "%LOG%" echo == starting the app
"%ADB%" -s %TV% shell am start -n %PKG%/.MainActivity >> "%LOG%" 2>&1
> logs\install.status echo 0
echo INSTALL DONE. The settings screen opened on the TV. Details: %LOG%
goto end

:noapk
> logs\install.status echo 3
>> "%LOG%" echo APK not found. Run scripts\build.bat first.
echo APK not found. Run scripts\build.bat first.
goto end
:notv
> logs\install.status echo 4
>> "%LOG%" echo scripts\tv.txt not found. Write the TV address into it (example: 192.168.1.50:5555).
echo scripts\tv.txt not found. Write the TV address into it (example: 192.168.1.50:5555).
goto end
:noadb
> logs\install.status echo 5
>> "%LOG%" echo adb not found. Is the Android SDK platform-tools installed?
echo adb not found. Is the Android SDK platform-tools installed?
goto end
:notconnected
> logs\install.status echo 6
echo Could not connect to the TV. Is Network debugging on under Developer options? Details: %LOG%
goto end
:installfail
> logs\install.status echo 7
echo Install failed. Details: %LOG%

:end
if /i "%~1"=="auto" exit /b 0
pause
