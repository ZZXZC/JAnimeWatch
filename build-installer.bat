@echo off
echo === JAnimeWatch Installer Builder ===
echo.

set JAVA_HOME=C:\Program Files\Java\jdk-21
set PATH=%JAVA_HOME%\bin;%PATH%

echo [1/4] Building with Maven...
call mvnw.cmd clean package -q
if %ERRORLEVEL% neq 0 (
    echo Build failed!
    exit /b 1
)

echo [2/4] Creating minimal JRE with jlink...
if exist target\jre rmdir /s /q target\jre
jlink --module-path "target/lib" ^
    --add-modules javafx.controls,javafx.fxml ^
    --output target/jre ^
    --no-header-files --no-man-pages --strip-debug --compress zip-6
if %ERRORLEVEL% neq 0 (
    echo jlink failed!
    exit /b 1
)

echo [3/4] Creating app image with jpackage...
if exist target\installer rmdir /s /q target\installer
jpackage --type app-image ^
    --name JAnimeWatch ^
    --input target ^
    --main-jar janimewatch-1.0-SNAPSHOT.jar ^
    --main-class com.janimewatch.App ^
    --dest target/installer ^
    --win-console ^
    --runtime-image target/jre
if %ERRORLEVEL% neq 0 (
    echo jpackage failed!
    exit /b 1
)

echo [4/4] Creating Windows installer...
jpackage --type exe ^
    --name JAnimeWatch ^
    --input target ^
    --main-jar janimewatch-1.0-SNAPSHOT.jar ^
    --main-class com.janimewatch.App ^
    --dest target/installer ^
    --win-console ^
    --win-shortcut ^
    --runtime-image target/jre
if %ERRORLEVEL% neq 0 (
    echo Installer creation failed, but app image was built.
)

echo.
echo Done! Check target\installer\
dir target\installer\*.exe 2>nul
dir target\installer\JAnimeWatch\*.exe 2>nul
pause
