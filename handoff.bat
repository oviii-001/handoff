@echo off
setlocal
set BIN_PATH=%~dp0cli\build\install\cli\bin\cli.bat

if not exist "%BIN_PATH%" (
    echo Building HandOff CLI... >&2
    call "%~dp0gradlew.bat" :cli:installDist -q
)

call "%BIN_PATH%" %*
endlocal
