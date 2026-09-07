param([switch]$Headless, [ValidateSet('host','software')][string]$Gpu = 'host')
. (Join-Path $PSScriptRoot 'environment.ps1')
$sdk = Find-KivoSdk
$toolRoot = Initialize-KivoTools
$adb = Join-Path $sdk 'platform-tools/adb.exe'
$emulator = Join-Path $toolRoot 'android-runtime/emulator/emulator.exe'
if (-not (Test-Path -LiteralPath $emulator)) { $emulator = Join-Path $sdk 'emulator/emulator.exe' }
$imagePath = Join-Path $sdk 'system-images/android-36.1/google_apis_playstore/x86_64'
if (-not (Test-Path -LiteralPath $imagePath)) { throw '缺少 Android 36.1 Google Play x86_64 镜像。请按构建与环境文档安装；本脚本不会下载大型组件。' }
$version = Get-KivoVersion
$apk = Join-Path $KivoProjectRoot "artifacts/kivo-archive-$version-preview.apk"
if (-not (Test-Path -LiteralPath $apk)) { throw '找不到交付 APK，请先运行 tools/build.ps1 -Target Release。' }
$env:ANDROID_HOME = $sdk
$env:ANDROID_SDK_ROOT = $sdk
$env:ANDROID_AVD_HOME = Join-Path $toolRoot 'avd'
$avdPath = Join-Path $env:ANDROID_AVD_HOME 'Kivo_Archive_Phone.avd'
New-Item -ItemType Directory -Force -Path $avdPath | Out-Null
$serial = 'emulator-5558'
$devices = & $adb devices
$active = $devices | Where-Object { $_ -match '^emulator-5558\s+device$' }
if ($active) { Assert-KivoEmulator $adb $serial }
elseif ($devices | Where-Object { $_ -match '^emulator-5558\s+' }) { throw '5558 端口上的模拟器尚未就绪。请等待它启动完成后重试。' }
else {
    # 最小配置显式指定系统版本；旧 avdmanager 对 36.1 会生成 android-0 与不兼容的图形默认值。
    if (-not (Test-Path -LiteralPath (Join-Path $avdPath 'config.ini'))) {
        $config = @('AvdId=Kivo_Archive_Phone','avd.ini.displayname=Kivo Archive Acceptance','avd.ini.encoding=UTF-8','PlayStore.enabled=true','abi.type=x86_64','hw.cpu.arch=x86_64','hw.cpu.ncore=4','hw.ramSize=2048','hw.gpu.enabled=yes','hw.gpu.mode=auto','hw.keyboard=yes','hw.lcd.density=420','hw.lcd.width=1080','hw.lcd.height=2400','hw.mainKeys=no','hw.camera.back=none','hw.camera.front=none','disk.dataPartition.size=4G','skin.name=1080x2400','skin.path=1080x2400','skin.dynamic=yes','showDeviceFrame=yes','tag.id=google_apis_playstore','target=android-36.1',"image.sysdir.1=$imagePath")
        Set-Content -LiteralPath (Join-Path $avdPath 'config.ini') -Value $config -Encoding ascii
    }
    Set-Content -LiteralPath (Join-Path $env:ANDROID_AVD_HOME 'Kivo_Archive_Phone.ini') -Value @('avd.ini.encoding=UTF-8',"path=$avdPath",'target=android-36.1') -Encoding ascii
    $logs = Join-Path $KivoProjectRoot 'artifacts/foundation'
    New-Item -ItemType Directory -Force -Path $logs | Out-Null
    $arguments = @('-avd','Kivo_Archive_Phone','-port','5558','-no-snapshot','-no-boot-anim','-gpu',$Gpu,'-no-audio','-timezone','Asia/Hong_Kong')
    if ($Headless) { $arguments += '-no-window' }
    Write-Host '正在启动古书馆专用模拟器，首次约需 1 分钟……'
    $window = if ($Headless) { 'Hidden' } else { 'Normal' }
    Start-Process -FilePath $emulator -ArgumentList $arguments -WindowStyle $window -RedirectStandardOutput (Join-Path $logs 'preview.stdout.log') -RedirectStandardError (Join-Path $logs 'preview.stderr.log') | Out-Null
}
$ready = $false
for ($i = 0; $i -lt 120; $i++) {
    try {
        $state = & $adb -s $serial get-state 2>$null
        if ($state -eq 'device') {
            $boot = & $adb -s $serial shell getprop sys.boot_completed 2>$null
            if (([string]$boot).Trim() -eq '1') { $ready = $true; break }
        }
    } catch {
        # PowerShell 5.1 会把 ADB 暂时找不到启动中的设备视为异常；这是轮询的正常等待状态。
        # 安装和启动阶段仍严格检查错误，超过总等待时间也会明确失败。
    }
    Start-Sleep -Seconds 1
}
if (-not $ready) { throw '模拟器未在两分钟内就绪，请查看 artifacts/foundation/preview.stderr.log。无需修改系统安全设置。' }
Assert-KivoEmulator $adb $serial
& $adb -s $serial install -r $apk
if ($LASTEXITCODE -ne 0) { throw '安装失败，保留现场。请查看上面的 Android 安装错误。' }
# 挂机后屏幕可能休眠；只唤醒本项目模拟器，不修改其锁屏或系统电源设置。
& $adb -s $serial shell input keyevent KEYCODE_WAKEUP
& $adb -s $serial shell am start -W -n 'wiki.kivo.app.preview/wiki.kivo.app.MainActivity'
if ($LASTEXITCODE -ne 0) { throw '启动失败，请查看验收说明的排查步骤。' }
Write-Host '古书馆已打开。鼠标拖动可滚动，底部五个入口可以直接操作。'
if ($active) { Write-Host '已复用正在运行的专用模拟器；若当前是无窗口模式，请先运行 tools/stop-preview.ps1，再重新打开电脑验收.cmd。' }
