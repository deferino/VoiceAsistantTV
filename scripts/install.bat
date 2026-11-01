@echo off
rem APK'yi TV'ye kurar ve asistan tusunu / servisleri bu uygulamaya baglar.
rem Once scripts\tv.txt dosyasina TV'nin adres:port degerini yaz (ornek: 192.168.1.50:5555).
rem Istege bagli: proje kokune config.json koy (API anahtari); TV'ye otomatik aktarilir.
rem Kullanim: scripts\install.bat   |   scripts\install.bat auto
setlocal EnableExtensions
cd /d "%~dp0.."
if not exist logs mkdir logs
set "LOG=logs\install.log"
set "PKG=com.voiceasistanttv.stt"
set "APK=app\build\outputs\apk\debug\app-debug.apk"
> "%LOG%" echo [%date% %time%] kurulum basliyor

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
>> "%LOG%" echo == config.json aktariliyor
"%ADB%" -s %TV% push config.json /data/local/tmp/vt-config.json >> "%LOG%" 2>&1
"%ADB%" -s %TV% shell chmod 644 /data/local/tmp/vt-config.json >> "%LOG%" 2>&1
"%ADB%" -s %TV% shell run-as %PKG% mkdir -p files >> "%LOG%" 2>&1
"%ADB%" -s %TV% shell "run-as %PKG% cp /data/local/tmp/vt-config.json files/config.json" >> "%LOG%" 2>&1
"%ADB%" -s %TV% shell rm -f /data/local/tmp/vt-config.json >> "%LOG%" 2>&1
:nocfg

>> "%LOG%" echo == tv-setup.sh
"%ADB%" -s %TV% push scripts\tv-setup.sh /data/local/tmp/vt-setup.sh >> "%LOG%" 2>&1
"%ADB%" -s %TV% shell sh /data/local/tmp/vt-setup.sh >> "%LOG%" 2>&1

rem NOT: burada "am force-stop" KULLANMA. Asistan/tanima servisi durdurulursa Android ayarlari Google'a geri alir.
>> "%LOG%" echo == uygulama baslatiliyor
"%ADB%" -s %TV% shell am start -n %PKG%/.MainActivity >> "%LOG%" 2>&1
> logs\install.status echo 0
echo KURULUM TAMAM. TV'de ayarlar ekrani acildi. Ayrinti: %LOG%
goto end

:noapk
> logs\install.status echo 3
>> "%LOG%" echo APK yok. Once scripts\build.bat calistir.
echo APK yok. Once scripts\build.bat calistir.
goto end
:notv
> logs\install.status echo 4
>> "%LOG%" echo scripts\tv.txt yok. Icine TV adresini yaz (ornek: 192.168.1.50:5555).
echo scripts\tv.txt yok. Icine TV adresini yaz (ornek: 192.168.1.50:5555).
goto end
:noadb
> logs\install.status echo 5
>> "%LOG%" echo adb bulunamadi. Android SDK platform-tools kurulu mu?
echo adb bulunamadi. Android SDK platform-tools kurulu mu?
goto end
:notconnected
> logs\install.status echo 6
echo TV'ye baglanilamadi. TV'de Gelistirici secenekleri - Ag hata ayiklama acik mi? Ayrinti: %LOG%
goto end
:installfail
> logs\install.status echo 7
echo Kurulum basarisiz. Ayrinti: %LOG%

:end
if /i "%~1"=="auto" exit /b 0
pause
