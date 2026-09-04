@echo off
setlocal
@chcp 65001 >nul 2>&1
set JAVA_OPTS=-Dfile.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8 %JAVA_OPTS%
set BIN_PATH=%~dp0cli\build\install\cli\bin\cli.bat

if not exist "%BIN_PATH%" (
    echo Building HandOff CLI... >&2
    call "%~dp0gradlew.bat" :cli:installDist -q
)

call "%BIN_PATH%" %*
endlocal
