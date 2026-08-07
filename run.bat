@echo off
title JAnimeWatch
setlocal

set JAVA_HOME=C:\Program Files\Java\jdk-21
set JAR=target\janimewatch-1.0-SNAPSHOT.jar
set LIB=target\lib

if not exist "%JAVA_HOME%\bin\java.exe" (
    echo Java 21 not found at %JAVA_HOME%
    echo Please set JAVA_HOME to your JDK 21+ installation
    pause
    exit /b 1
)

if not exist "%JAR%" (
    echo JAR not found. Run: mvn clean package
    pause
    exit /b 1
)

"%JAVA_HOME%\bin\java.exe" ^
    --module-path "%LIB%" ^
    --add-modules javafx.controls,javafx.fxml ^
    -jar "%JAR%"

if errorlevel 1 (
    echo.
    echo Application exited with errors.
    pause
)
