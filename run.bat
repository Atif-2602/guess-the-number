@echo off
setlocal
cd /d "%~dp0"

if not exist out (
    call build.bat
    if errorlevel 1 exit /b 1
)

echo.
echo Starting web server... your browser will open automatically.
echo Press Ctrl+C to stop the server.
echo.
java -cp out guessnumber.web.WebServer %*
endlocal
