@echo off
setlocal
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0mobile_dev.ps1" %*
exit /b %ERRORLEVEL%
