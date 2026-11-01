@echo off
rem Focused test: are the settings in place, and what happens when the microphone key is pressed? -> logs\key-test.log
setlocal EnableExtensions
cd /d "%~dp0.."
if not exist logs mkdir logs
set "LOG=logs\key-test.log"
> "%LOG%" echo [%date% %time%] key test
if not exist scripts\tv.txt goto notv
set /p TV=<scripts\tv.txt
set "ADB="
where adb >nul 2>&1 && set "ADB=adb"
if not defined ADB if defined ANDROID_HOME if exist "%ANDROID_HOME%\platform-tools\adb.exe" set "ADB=%ANDROID_HOME%\platform-tools\adb.exe"
if not defined ADB if exist "%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe" set "ADB=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe"
if not defined ADB goto noadb
"%ADB%" connect %TV% >> "%LOG%" 2>&1

>> "%LOG%" echo ===== SETTINGS =====
call :sh "settings get secure assistant; settings get secure voice_interaction_service; settings get secure voice_recognition_service; settings get secure enabled_accessibility_services"
call :sh "dumpsys voiceinteraction | grep -E 'mComponent=|Session service=|mBound=' | head -6"

>> "%LOG%" echo ===== TEST_1_input_keyevent_ASSIST =====
"%ADB%" -s %TV% logcat -c
call :sh "input keyevent KEYCODE_ASSIST"
ping -n 4 127.0.0.1 >nul
call :top
call :vt
"%ADB%" -s %TV% shell input keyevent KEYCODE_BACK
ping -n 3 127.0.0.1 >nul

>> "%LOG%" echo ===== TEST_2_PHYSICAL_MIC_KEY =====
"%ADB%" -s %TV% logcat -c
echo.
echo ===========================================================
echo  NOW press the remote's MICROPHONE key ONCE and release it.
echo  Note what appears on the screen. Waiting 10 seconds.
echo ===========================================================
"%ADB%" -s %TV% shell "timeout 10 getevent -lt" > logs\key-getevent.log 2>&1
call :top
call :vt
>> "%LOG%" echo -- system log (key / assistant lines):
call :sh "logcat -d -v threadtime | grep -E 'TclPhoneWindowManager|TTVS_LOG|VoiceInteraction|katniss|Katniss|voicetv|VoiceTV|ActivityTaskManager|AssistantWrapper' | grep -v -E 'IpelManager|AssistStructure' | cut -c1-260 | tail -60"
>> "%LOG%" echo -- getevent:
type logs\key-getevent.log >> "%LOG%"
echo.
echo Done: %LOG%
goto end

:sh
"%ADB%" -s %TV% shell "%~1" >> "%LOG%" 2>&1
exit /b 0
:top
>> "%LOG%" echo -- top screen:
"%ADB%" -s %TV% shell "dumpsys activity activities | grep -E 'topResumedActivity|mResumedActivity'" >> "%LOG%" 2>&1
exit /b 0
:vt
>> "%LOG%" echo -- app log:
"%ADB%" -s %TV% shell "logcat -d -s VoiceTV:I" >> "%LOG%" 2>&1
exit /b 0
:notv
echo scripts\tv.txt not found.
goto end
:noadb
echo adb not found.
:end
if /i "%~1"=="auto" exit /b 0
pause
