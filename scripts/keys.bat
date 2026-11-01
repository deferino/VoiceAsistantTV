@echo off
rem 15 saniye boyunca kumandadan gelen ham tus olaylarini logs\keys.log dosyasina yazar.
rem Calistirinca hemen kumandadaki asistan/mikrofon tusuna ve birkac baska tusa bas.
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
echo Simdi 15 saniye boyunca kumandadaki tuslara bas (ozellikle asistan/mikrofon tusu)...
"%ADB%" -s %TV% shell timeout 15 getevent -lt > logs\keys.log 2>&1
echo Bitti: logs\keys.log
goto end
:notv
echo scripts\tv.txt yok.
goto end
:noadb
echo adb bulunamadi.
:end
if /i "%~1"=="auto" exit /b 0
pause
