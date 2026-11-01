@echo off
rem After a code change: build and install on the TV (asks no test question).
setlocal EnableExtensions
cd /d "%~dp0.."
if exist logs\build.status del logs\build.status
call scripts\build.bat auto
set /p BS=<logs\build.status
if not "%BS%"=="0" goto fail
call scripts\install.bat auto
echo.
echo UPDATE DONE. Press the assistant key on the TV to try it.
goto end
:fail
echo BUILD FAILED - see logs\build.log
:end
pause
