@echo off
rem TEK TIK: derle -> TV'ye kur -> asistan olarak ata -> mikrofon tusu testi.
setlocal EnableExtensions
cd /d "%~dp0.."
if exist logs\build.status del logs\build.status
call scripts\build.bat auto
set /p BS=<logs\build.status
if not "%BS%"=="0" goto fail
call scripts\install.bat auto
call scripts\test-key.bat auto
echo.
echo HEPSI BITTI. Bana "bitti" yaz, loglari okuyayim.
goto end
:fail
echo DERLEME BASARISIZ - logs\build.log dosyasini bana bildir.
:end
pause
