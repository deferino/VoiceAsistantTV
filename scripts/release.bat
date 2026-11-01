@echo off
rem Yayin (release) APK'sini derler ve dist\ klasorune kurulum paketini hazirlar:
rem   dist\SesliArama.apk, kurulum.bat, install.bat, tv-setup.sh, restore-defaults.sh, BENIOKU.txt, README.txt
rem   dist\SesliArama-kurulum.zip   (GitHub Releases'e yuklenecek dosya)
rem Kullanim: scripts\release.bat        (islem sonunda bekler)
rem           scripts\release.bat auto   (beklemeden kapanir)
setlocal EnableExtensions
cd /d "%~dp0.."
if not exist logs mkdir logs
> logs\release.log echo [%date% %time%] release basliyor

rem --- JDK: Android Studio'nun JBR'si varsa onu kullan
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
copy /y "%APK%" dist\SesliArama.apk >nul
copy /y kurulum.bat dist\kurulum.bat >nul
copy /y install.bat dist\install.bat >nul
copy /y scripts\tv-setup.sh dist\tv-setup.sh >nul
copy /y scripts\restore-defaults.sh dist\restore-defaults.sh >nul
copy /y scripts\dist-benioku.txt dist\BENIOKU.txt >nul
copy /y scripts\dist-readme-en.txt dist\README.txt >nul
powershell -NoProfile -ExecutionPolicy Bypass -Command "Compress-Archive -Force -DestinationPath 'dist\SesliArama-kurulum.zip' -Path 'dist\SesliArama.apk','dist\kurulum.bat','dist\install.bat','dist\tv-setup.sh','dist\restore-defaults.sh','dist\BENIOKU.txt','dist\README.txt'" >> logs\release.log 2>&1
if errorlevel 1 goto fail
> logs\release.status echo 0
for %%F in (dist\SesliArama.apk) do echo RELEASE TAMAM: dist\SesliArama-kurulum.zip - APK %%~zF bayt
for %%F in (dist\SesliArama.apk) do >> logs\release.log echo APK boyutu: %%~zF bayt
goto end

:nosdk
set "RC=2"
> logs\release.status echo 2
echo ANDROID_HOME bulunamadi. Android Studio kurulu mu?
goto end
:noapk
set "RC=3"
> logs\release.status echo 3
echo Release APK bulunamadi - ayrinti: logs\release.log
goto end
:fail
if "%RC%"=="0" set "RC=1"
> logs\release.status echo %RC%
echo RELEASE HATA - ayrinti: logs\release.log

:end
if /i "%~1"=="auto" exit /b %RC%
pause
exit /b %RC%
