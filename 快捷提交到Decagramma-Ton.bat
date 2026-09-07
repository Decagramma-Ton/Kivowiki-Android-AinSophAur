@echo off
setlocal EnableExtensions DisableDelayedExpansion
chcp 65001 >nul
cd /d "%~dp0"

set "EXPECTED_REMOTE=https://github.com/Decagramma-Ton/Kivowiki-Android-AinSophAur.git"

git rev-parse --show-toplevel >nul 2>&1
if errorlevel 1 (
    echo [错误] 当前目录不是 Git 仓库。
    pause
    exit /b 1
)

for /f "delims=" %%R in ('git remote get-url origin 2^>nul') do set "CURRENT_REMOTE=%%R"
if not defined CURRENT_REMOTE (
    echo [错误] 未配置 origin 远端。
    pause
    exit /b 1
)
if /i not "%CURRENT_REMOTE%"=="%EXPECTED_REMOTE%" (
    echo [错误] origin 不是预期仓库：
    echo   %CURRENT_REMOTE%
    pause
    exit /b 1
)

git config user.name "The One"
git config user.email "326039646+Decagramma-Ton@users.noreply.github.com"

echo.
echo 当前改动：
git status --short
git diff --stat

set /p "COMMIT_MESSAGE=请输入提交说明（留空取消）："
if not defined COMMIT_MESSAGE (
    echo 已取消，未暂存或提交任何文件。
    pause
    exit /b 0
)

git add -A
git diff --cached --quiet
if not errorlevel 1 (
    echo 没有可提交的改动。
    pause
    exit /b 0
)

echo.
echo 即将提交以下内容：
git diff --cached --stat
choice /C YN /M "确认提交并推送到 Decagramma-Ton"
if errorlevel 2 (
    echo 已取消。改动已暂存，可执行 git restore --staged . 取消暂存。
    pause
    exit /b 0
)

git commit -m "%COMMIT_MESSAGE%"
if errorlevel 1 (
    echo [错误] 提交失败，未执行推送。
    pause
    exit /b 1
)

git -c credential.username=Decagramma-Ton push -u origin main
if errorlevel 1 (
    echo [错误] 推送失败。请完成 GitHub 授权后重试。
    pause
    exit /b 1
)

echo.
echo 提交并推送完成：
git log -1 --format="%%h %%an ^<%%ae^> %%s"
pause
