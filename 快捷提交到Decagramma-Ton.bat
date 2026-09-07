@echo off
setlocal EnableExtensions DisableDelayedExpansion
chcp 65001 >nul
cd /d "%~dp0"

set "EXPECTED_REMOTE=https://github.com/Decagramma-Ton/Kivowiki-Android-AinSophAur.git"

git rev-parse --show-toplevel >nul 2>&1
if errorlevel 1 (
    echo [ERROR] This file must be run from inside the project repository.
    pause
    exit /b 1
)

for /f "delims=" %%R in ('git remote get-url origin 2^>nul') do set "CURRENT_REMOTE=%%R"
if not defined CURRENT_REMOTE (
    echo [ERROR] The origin remote is not configured.
    pause
    exit /b 1
)
if /i not "%CURRENT_REMOTE%"=="%EXPECTED_REMOTE%" (
    echo [ERROR] The origin remote does not match the expected repository:
    echo   %CURRENT_REMOTE%
    pause
    exit /b 1
)

git config user.name "Decagrammaton"
git config user.email "326039646+Decagramma-Ton@users.noreply.github.com"

echo.
echo Current changes:
git status --short
git diff --stat

set /p "COMMIT_MESSAGE=Commit message (blank to cancel): "
if not defined COMMIT_MESSAGE (
    echo Cancelled. No files were staged or committed.
    pause
    exit /b 0
)

git add -A
git diff --cached --quiet
if not errorlevel 1 (
    echo There are no changes to commit.
    pause
    exit /b 0
)

echo.
echo Changes to be committed:
git diff --cached --stat
choice /C YN /M "Commit and push to Decagramma-Ton"
if errorlevel 2 (
    echo Cancelled. Files remain staged; run git restore --staged . to unstage them.
    pause
    exit /b 0
)

git commit -m "%COMMIT_MESSAGE%"
if errorlevel 1 (
    echo [ERROR] Commit failed. Nothing was pushed.
    pause
    exit /b 1
)

git -c credential.username=Decagramma-Ton push -u origin main
if errorlevel 1 (
    echo [ERROR] Push failed. Complete GitHub authorization and try again.
    pause
    exit /b 1
)

echo.
echo Commit and push completed:
git log -1 --format="%%h %%an ^<%%ae^> %%s"
pause
