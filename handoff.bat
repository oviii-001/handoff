@echo off
setlocal
set BIN_PATH=%~dp0desktopApp\build\install\desktopApp\bin\desktopApp.bat

if not exist "%BIN_PATH%" (
    echo [Handoff] Building desktop CLI...
    call "%~dp0gradlew.bat" :desktopApp:installDist -q
)

call "%BIN_PATH%" %*
endlocal
