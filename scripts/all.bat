@echo off
rem ONE CLICK: build -> install on the TV -> set as assistant -> mic key test.
setlocal EnableExtensions
cd /d "%~dp0.."
if exist logs\build.status del logs\build.status
call scripts\build.bat auto
set /p BS=<logs\build.status
if not "%BS%"=="0" goto fail
call scripts\install.bat auto
call scripts\test-key.bat auto
echo.
echo ALL DONE. The logs are in the logs folder.
goto end
:fail
echo BUILD FAILED - see logs\build.log
:end
pause
