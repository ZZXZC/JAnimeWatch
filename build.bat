@echo off
title JAnimeWatch Builder
setlocal

set JAVA_HOME=C:\Program Files\Java\jdk-21

echo ===================================
echo   JAnimeWatch Build
echo ===================================
echo.

if not exist "%JAVA_HOME%\bin\java.exe" (
    echo Java 21 not found at %JAVA_HOME%
    pause
    exit /b 1
)

echo Building with Maven...
call .\mvnw.cmd clean package -q

if errorlevel 1 (
    echo.
    echo BUILD FAILED
    pause
    exit /b 1
)

echo.
echo BUILD SUCCESS
echo.
echo To run: double-click run.bat
echo Or: .\mvnw.cmd javafx:run
echo.
pause
