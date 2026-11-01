@echo off
rem Builds the app (debug APK). Output: app\build\outputs\apk\debug\app-debug.apk
rem Usage: scripts\build.bat        (waits at the end)
rem           scripts\build.bat auto   (closes without waiting)
setlocal EnableExtensions
cd /d "%~dp0.."
if not exist logs mkdir logs
> logs\build.log echo [%date% %time%] build starting

rem --- JDK: use Android Studio's bundled JBR if present
if not defined JAVA_HOME if exist "%ProgramFiles%\Android\Android Studio\jbr\bin\java.exe" set "JAVA_HOME=%ProgramFiles%\Android\Android Studio\jbr"
if not defined JAVA_HOME if exist "%LOCALAPPDATA%\Programs\Android Studio\jbr\bin\java.exe" set "JAVA_HOME=%LOCALAPPDATA%\Programs\Android Studio\jbr"
if not defined JAVA_HOME if exist "%ProgramFiles%\Android\Android Studio\jre\bin\java.exe" set "JAVA_HOME=%ProgramFiles%\Android\Android Studio\jre"

rem --- Android SDK
if not defined ANDROID_HOME if defined ANDROID_SDK_ROOT set "ANDROID_HOME=%ANDROID_SDK_ROOT%"
if not defined ANDROID_HOME if exist "%LOCALAPPDATA%\Android\Sdk" set "ANDROID_HOME=%LOCALAPPDATA%\Android\Sdk"
if not defined ANDROID_HOME goto nosdk

if exist local.properties goto havelocal
set "SDKP=%ANDROID_HOME:\=/%"
> local.properties echo sdk.dir=%SDKP%
:havelocal

rem --- accept SDK licenses (if cmdline-tools is installed)
set "SDKM=%ANDROID_HOME%\cmdline-tools\latest\bin\sdkmanager.bat"
if not exist "%SDKM%" goto nolic
echo y| call "%SDKM%" --licenses >nul 2>&1
:nolic

>> logs\build.log echo ANDROID_HOME=%ANDROID_HOME%
>> logs\build.log echo JAVA_HOME=%JAVA_HOME%
call gradlew.bat assembleDebug --console=plain >> logs\build.log 2>&1
set "RC=%ERRORLEVEL%"
> logs\build.status echo %RC%
if "%RC%"=="0" goto ok
echo BUILD FAILED - see logs\build.log
goto end
:ok
echo BUILD OK: app\build\outputs\apk\debug\app-debug.apk
goto end

:nosdk
> logs\build.status echo 2
>> logs\build.log echo ANDROID_HOME not found. Install Android Studio or set the ANDROID_HOME environment variable.
echo ANDROID_HOME not found. Is Android Studio installed?

:end
if /i "%~1"=="auto" exit /b %RC%
pause
exit /b %RC%
