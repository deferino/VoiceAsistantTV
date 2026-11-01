@echo off
rem Kod degisince: derle ve TV'ye kur (test sorusu sormaz).
setlocal EnableExtensions
cd /d "%~dp0.."
if exist logs\build.status del logs\build.status
call scripts\build.bat auto
set /p BS=<logs\build.status
if not "%BS%"=="0" goto fail
call scripts\install.bat auto
echo.
echo GUNCELLEME TAMAM. TV'de asistan tusuna basip dene.
goto end
:fail
echo DERLEME BASARISIZ - logs\build.log dosyasini bana bildir.
:end
pause
