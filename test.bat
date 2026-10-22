@echo off
setlocal
cd /d "%~dp0"

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0tests\api-test.ps1" %*
if errorlevel 1 (
    echo.
    echo TESTS FAILED.
    exit /b 1
)

echo.
echo All tests passed.
endlocal
