@echo off
setlocal enabledelayedexpansion

echo ========================================================
echo   COMPILING INDIC MESH VOICE APK AND COPYING TO ROOT
echo ========================================================

for /d %%i in (.jdk17\jdk-17*) do (
    set "JAVA_HOME=%%~fi"
)

if not defined JAVA_HOME (
    for /d %%i in (.jdk17\*) do (
        set "JAVA_HOME=%%~fi"
    )
)

echo Using JDK at: %JAVA_HOME%
set "PATH=%JAVA_HOME%\bin;%PATH%"

echo Building assembleDebug APK...
call gradlew.bat assembleDebug

if exist "app\build\outputs\apk\debug\app-debug.apk" (
    copy /Y "app\build\outputs\apk\debug\app-debug.apk" "app-debug.apk"
    echo.
    echo ========================================================
    echo  SUCCESS! APK COPIED TO DEMO APP FOLDER:
    echo  d:\Smart India Hackathon\demo app\app-debug.apk
    echo ========================================================
) else (
    echo [ERROR] APK compilation failed. Please inspect logs above.
)
