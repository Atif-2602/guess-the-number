@echo off
setlocal
cd /d "%~dp0"

echo.
echo Compiling Java sources...
if exist out rmdir /s /q out
mkdir out

javac -encoding UTF-8 -d out src\guessnumber\*.java src\guessnumber\web\*.java
if errorlevel 1 (
    echo.
    echo BUILD FAILED. Make sure a JDK 17+ is installed and "javac" is on PATH.
    exit /b 1
)

echo Build successful. Classes are in the "out" folder.
echo.
endlocal
