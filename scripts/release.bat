@echo off
rem Builds the release APK and prepares the installer package in dist\ :
rem   dist\VoiceSearch.apk, install.bat, tv-setup.sh, restore-defaults.sh, README.txt
rem   dist\VoiceSearch-installer.zip   (the file to upload to GitHub Releases)
rem Usage: scripts\release.bat        (waits at the end)
rem        scripts\release.bat auto   (closes without waiting)
setlocal EnableExtensions
cd /d "%~dp0.."
if not exist logs mkdir logs
> logs\release.log echo [%date% %time%] release starting

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

call gradlew.bat assembleRelease --console=plain >> logs\release.log 2>&1
set "RC=%ERRORLEVEL%"
if not "%RC%"=="0" goto fail
set "APK=app\build\outputs\apk\release\app-release.apk"
if not exist "%APK%" goto noapk

if exist dist rmdir /s /q dist
mkdir dist
copy /y "%APK%" dist\VoiceSearch.apk >nul
copy /y install.bat dist\install.bat >nul
copy /y scripts\tv-setup.sh dist\tv-setup.sh >nul
copy /y scripts\restore-defaults.sh dist\restore-defaults.sh >nul
copy /y scripts\dist-readme.txt dist\README.txt >nul
powershell -NoProfile -ExecutionPolicy Bypass -Command "Compress-Archive -Force -DestinationPath 'dist\VoiceSearch-installer.zip' -Path 'dist\VoiceSearch.apk','dist\install.bat','dist\tv-setup.sh','dist\restore-defaults.sh','dist\README.txt'" >> logs\release.log 2>&1
if errorlevel 1 goto fail
> logs\release.status echo 0
for %%F in (dist\VoiceSearch.apk) do echo RELEASE OK: dist\VoiceSearch-installer.zip - APK %%~zF bytes
for %%F in (dist\VoiceSearch.apk) do >> logs\release.log echo APK size: %%~zF bytes
goto end

:nosdk
set "RC=2"
> logs\release.status echo 2
echo ANDROID_HOME not found. Is Android Studio installed?
goto end
:noapk
set "RC=3"
> logs\release.status echo 3
echo Release APK not found - see logs\release.log
goto end
:fail
if "%RC%"=="0" set "RC=1"
> logs\release.status echo %RC%
echo RELEASE FAILED - see logs\release.log

:end
if /i "%~1"=="auto" exit /b %RC%
pause
exit /b %RC%
