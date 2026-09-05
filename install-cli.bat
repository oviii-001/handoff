@echo off
setlocal
echo Installing HandOff CLI globally for Windows...

set TARGET_DIR=%LOCALAPPDATA%\Microsoft\WindowsApps
set SHIM_FILE=%TARGET_DIR%\handoff.cmd
set REPO_DIR=%~dp0
set HANDOFF_BAT=%REPO_DIR%handoff.bat

if not exist "%HANDOFF_BAT%" (
    echo Error: Could not find handoff.bat at "%HANDOFF_BAT%"
    exit /b 1
)

if not exist "%TARGET_DIR%" (
    mkdir "%TARGET_DIR%" 2>nul
)

(
    echo @echo off
    echo call "%HANDOFF_BAT%" %%*
) > "%SHIM_FILE%"

if %ERRORLEVEL% NEQ 0 (
    echo Error writing shim to %SHIM_FILE%
    exit /b 1
)

echo.
echo =======================================================
echo  SUCCESS: HandOff CLI installed to WindowsApps PATH!
echo.
echo  You can now run 'handoff' directly from ANY terminal:
echo    handoff --pair
echo    handoff --status
echo    handoff --doctor
echo.
echo  No prefix like .\ is needed anymore.
echo =======================================================

endlocal
