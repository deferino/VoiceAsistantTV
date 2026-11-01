@echo off
rem Same installer as kurulum.bat (English entry point). Language follows Windows; force with VT_LANG=en or tr.
rem Usage: install.bat            install or update (double-click is enough)
rem        install.bat uninstall  restore the previous assistant settings and remove the app
call "%~dp0kurulum.bat" %*
exit /b %ERRORLEVEL%
