@echo off
setlocal
cd /d "%~dp0"

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0tools\deploy-pages.ps1" %*
if errorlevel 1 (
    echo.
    echo DEPLOY FAILED.
    exit /b 1
)

endlocal
