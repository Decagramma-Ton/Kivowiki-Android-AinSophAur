@echo off
setlocal EnableExtensions DisableDelayedExpansion
chcp 65001 >nul
for %%I in ("%~dp0.") do set "PROJECT_DIR=%%~fI"
cd /d "%PROJECT_DIR%"

set "EXPECTED_REMOTE=https://github.com/Decagramma-Ton/Kivowiki-Android-AinSophAur.git"

git -c "safe.directory=%PROJECT_DIR%" -C "%PROJECT_DIR%" rev-parse --show-toplevel >nul
if errorlevel 1 (
    echo [ERROR] Git cannot open the repository shown above:
    echo   %PROJECT_DIR%
    pause
    exit /b 1
)

for /f "delims=" %%R in ('git -c "safe.directory=%PROJECT_DIR%" -C "%PROJECT_DIR%" remote get-url origin 2^>nul') do set "CURRENT_REMOTE=%%R"
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

git -c "safe.directory=%PROJECT_DIR%" -C "%PROJECT_DIR%" config user.name "Decagrammaton"
git -c "safe.directory=%PROJECT_DIR%" -C "%PROJECT_DIR%" config user.email "326039646+Decagramma-Ton@users.noreply.github.com"

echo.
echo Current changes:
git -c "safe.directory=%PROJECT_DIR%" -C "%PROJECT_DIR%" status --short
git -c "safe.directory=%PROJECT_DIR%" -C "%PROJECT_DIR%" diff --stat

set /p "COMMIT_MESSAGE=Commit message (blank to cancel): "
if not defined COMMIT_MESSAGE (
    echo Cancelled. No files were staged or committed.
    pause
    exit /b 0
)

git -c "safe.directory=%PROJECT_DIR%" -C "%PROJECT_DIR%" add -A
git -c "safe.directory=%PROJECT_DIR%" -C "%PROJECT_DIR%" diff --cached --quiet
if not errorlevel 1 (
    echo There are no changes to commit.
    pause
    exit /b 0
)

echo.
echo Changes to be committed:
git -c "safe.directory=%PROJECT_DIR%" -C "%PROJECT_DIR%" diff --cached --stat
choice /C YN /M "Commit and push to Decagramma-Ton"
if errorlevel 2 (
    echo Cancelled. Files remain staged; run git restore --staged . to unstage them.
    pause
    exit /b 0
)

git -c "safe.directory=%PROJECT_DIR%" -C "%PROJECT_DIR%" commit -m "%COMMIT_MESSAGE%"
if errorlevel 1 (
    echo [ERROR] Commit failed. Nothing was pushed.
    pause
    exit /b 1
)

git -c "safe.directory=%PROJECT_DIR%" -c credential.username=Decagramma-Ton -C "%PROJECT_DIR%" push -u origin main
if errorlevel 1 (
    echo [ERROR] Push failed. Complete GitHub authorization and try again.
    pause
    exit /b 1
)

echo.
echo Commit and push completed:
git -c "safe.directory=%PROJECT_DIR%" -C "%PROJECT_DIR%" log -1 --format="%%h %%an ^<%%ae^> %%s"
pause
