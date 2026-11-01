@echo off
rem Why is the assistant key not taken over? Collects all diagnostics in one go -> logs\diag.log, logs\diag-keys.log, logs\diag-logcat.log
rem Follow the on-screen prompt: when asked, press the remote's MICROPHONE key 2-3 times.
setlocal EnableExtensions
cd /d "%~dp0.."
if not exist logs mkdir logs
set "LOG=logs\diag.log"
if not exist scripts\tv.txt goto notv
set /p TV=<scripts\tv.txt
set "ADB="
where adb >nul 2>&1 && set "ADB=adb"
if not defined ADB if defined ANDROID_HOME if exist "%ANDROID_HOME%\platform-tools\adb.exe" set "ADB=%ANDROID_HOME%\platform-tools\adb.exe"
if not defined ADB if exist "%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe" set "ADB=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe"
if not defined ADB goto noadb

> "%LOG%" echo [%date% %time%] diagnostics starting
"%ADB%" connect %TV% >> "%LOG%" 2>&1

call :h CIHAZ
call :sh "getprop ro.product.manufacturer; getprop ro.product.model; getprop ro.build.version.release; getprop ro.build.version.sdk; getprop ro.build.fingerprint"
call :sh "pm list features | grep -i -E 'leanback|television|google'"

call :h SETTINGS
call :sh "settings get secure assistant; settings get secure voice_interaction_service; settings get secure voice_recognition_service; settings get secure enabled_accessibility_services"
call :sh "cmd role get-role-holders --user 0 android.app.role.ASSISTANT"
call :sh "cmd package resolve-activity --brief -a android.intent.action.ASSIST"
call :sh "pm list packages | grep -i -E 'katniss|assistant|voice|tvlauncher|googletv|launcher|search'"

call :h VOICEINTERACTION
call :sh "dumpsys voiceinteraction | head -60"

call :h GLOBAL_KEYS
call :sh "cat /system/etc/global_keys.xml /product/etc/global_keys.xml /vendor/etc/global_keys.xml /odm/etc/global_keys.xml /system_ext/etc/global_keys.xml 2>&1 | head -60"

call :h GIRIS_AYGITLARI
call :sh "getevent -lp 2>&1 | grep -E 'add device|name:|ASSIST|VOICE|SEARCH|MIC' | head -60"
call :sh "ls /system/usr/keylayout /vendor/usr/keylayout /odm/usr/keylayout /product/usr/keylayout 2>&1 | head -80"

call :h TEST_A_cmd_voiceinteraction_show
"%ADB%" -s %TV% logcat -c
call :sh "cmd voiceinteraction show"
call :wait3
call :top
"%ADB%" -s %TV% shell input keyevent KEYCODE_BACK
call :wait3

call :h TEST_B_input_keyevent_ASSIST_219
call :sh "input keyevent KEYCODE_ASSIST"
call :wait3
call :top
"%ADB%" -s %TV% shell input keyevent KEYCODE_BACK
call :wait3

call :h TEST_C_input_keyevent_VOICE_ASSIST_231
call :sh "input keyevent KEYCODE_VOICE_ASSIST"
call :wait3
call :top
"%ADB%" -s %TV% shell input keyevent KEYCODE_BACK
call :wait3

call :h TEST_D_FIZIKSEL_KUMANDA_TUSU
"%ADB%" -s %TV% logcat -c
echo.
echo ===========================================================
echo  NOW: press the remote's MICROPHONE / ASSISTANT key 2-3 times.
echo  Listening for 15 seconds...
echo ===========================================================
"%ADB%" -s %TV% shell "timeout 15 getevent -lt" > logs\diag-keys.log 2>&1
call :top
"%ADB%" -s %TV% shell input keyevent KEYCODE_BACK

call :h REMOTE_KEYS_getevent
type logs\diag-keys.log >> "%LOG%"

"%ADB%" -s %TV% logcat -d -v threadtime > logs\diag-logcat.log 2>&1
call :h LOGCAT_suzulmus
call :sh "logcat -d -v threadtime | grep -i -E 'assist|voiceinter|katniss|voicetv|globalkey|GLOBAL_BUTTON|KeyEvent|ActivityTaskManager.*START|ActivityManager.*START' | tail -80"

echo.
echo DIAGNOSTICS DONE. Files: logs\diag.log  logs\diag-keys.log  logs\diag-logcat.log
goto end

:h
>> "%LOG%" echo.
>> "%LOG%" echo ===== %~1 =====
exit /b 0

:sh
"%ADB%" -s %TV% shell "%~1" >> "%LOG%" 2>&1
exit /b 0

:top
>> "%LOG%" echo -- top screen:
"%ADB%" -s %TV% shell "dumpsys activity activities | grep -E 'topResumedActivity|mResumedActivity|ResumedActivity'" >> "%LOG%" 2>&1
exit /b 0

:wait3
ping -n 4 127.0.0.1 >nul
exit /b 0

:notv
echo scripts\tv.txt not found.
goto end
:noadb
echo adb not found.
:end
if /i "%~1"=="auto" exit /b 0
pause
