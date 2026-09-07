@echo off
powershell.exe -NoProfile -File "%~dp0tools\build.ps1" -Target Release
pause
