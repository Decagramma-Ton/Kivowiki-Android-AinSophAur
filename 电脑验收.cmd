@echo off
powershell.exe -NoProfile -File "%~dp0tools\preview.ps1"
if errorlevel 1 pause
