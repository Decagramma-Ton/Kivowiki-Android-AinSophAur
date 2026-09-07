param([ValidateSet('Online','Offline','LargeFont','Tablet')][string]$Mode = 'Online')
. (Join-Path $PSScriptRoot 'environment.ps1')
Set-KivoBuildEnvironment
$adb = Join-Path $env:ANDROID_HOME 'platform-tools/adb.exe'
$serial = 'emulator-5558'
Assert-KivoEmulator $adb $serial
$env:ANDROID_SERIAL = $serial
$logRoot = Join-Path $KivoProjectRoot 'artifacts/character-0.3'
New-Item -ItemType Directory -Force -Path $logRoot | Out-Null

function Invoke-KivoInstrumentation([string]$TestClass, [string]$Name, [switch]$Offline, [string]$CapturePrefix = 'adaptive') {
    $arguments = @('-s',$serial,'shell','am','instrument','-w','-r','-e','class',$TestClass,'-e','capturePrefix',$CapturePrefix)
    if ($Offline) { $arguments += @('-e','expectOffline','true') }
    $arguments += 'wiki.kivo.app.preview.debug.test/androidx.test.runner.AndroidJUnitRunner'
    $output = & $adb @arguments 2>&1
    $output | Set-Content -LiteralPath (Join-Path $logRoot "$Name.log") -Encoding utf8
    $joined = $output -join "`n"
    Write-Host $joined
    # am instrument 的进程退出码不能单独代表 JUnit 成功，必须检查实际完成标记。
    if ($LASTEXITCODE -ne 0 -or $joined -notmatch 'OK \(\d+ tests?\)' -or $joined -match 'FAILURES!!!|INSTRUMENTATION_FAILED') {
        throw "设备用例失败，请查看 artifacts/character-0.3/$Name.log。"
    }
}

Push-Location $KivoProjectRoot
try {
    if ($Mode -eq 'Online') {
        & ./gradlew.bat :app:connectedDebugAndroidTest :core:data:connectedDebugAndroidTest --console=plain
        if ($LASTEXITCODE -ne 0) { throw '设备测试失败。' }
    } else {
        & ./gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest --console=plain
        if ($LASTEXITCODE -ne 0) { throw '设备测试包构建失败。' }
        & $adb -s $serial install -r app/build/outputs/apk/debug/app-debug.apk
        if ($LASTEXITCODE -ne 0) { throw 'Debug App 安装失败。' }
        & $adb -s $serial install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
        if ($LASTEXITCODE -ne 0) { throw '测试 APK 安装失败。' }
        switch ($Mode) {
            'Offline' {
                # 先实际读取公告列表和正文，再断开专用 AVD 的网络，验证缓存路径。
                Invoke-KivoInstrumentation 'wiki.kivo.app.OfflineAcceptanceTest' 'offline-warmup'
                $wifi = ([string](& $adb -s $serial shell settings get global wifi_on)).Trim()
                $mobile = ([string](& $adb -s $serial shell settings get global mobile_data)).Trim()
                try {
                    & $adb -s $serial shell svc wifi disable
                    & $adb -s $serial shell svc data disable
                    Start-Sleep -Seconds 2
                    Invoke-KivoInstrumentation 'wiki.kivo.app.OfflineAcceptanceTest' 'offline-final' -Offline
                } finally {
                    $wifiAction = if ($wifi -eq '0') { 'disable' } else { 'enable' }
                    $mobileAction = if ($mobile -eq '0') { 'disable' } else { 'enable' }
                    & $adb -s $serial shell svc wifi $wifiAction
                    & $adb -s $serial shell svc data $mobileAction
                }
            }
            'LargeFont' {
                $font = ([string](& $adb -s $serial shell settings get system font_scale)).Trim()
                if ($font -eq 'null' -or -not $font) { $font = '1.0' }
                try {
                    & $adb -s $serial shell settings put system font_scale 2.0
                    Invoke-KivoInstrumentation 'wiki.kivo.app.AdaptiveAcceptanceTest' 'font200-final' -CapturePrefix 'phone-font200'
                } finally { & $adb -s $serial shell settings put system font_scale $font }
            }
            'Tablet' {
                $sizeText = (& $adb -s $serial shell wm size) -join "`n"
                $densityText = (& $adb -s $serial shell wm density) -join "`n"
                $oldSize = if ($sizeText -match 'Override size: (\d+x\d+)') { $Matches[1] } else { 'reset' }
                $oldDensity = if ($densityText -match 'Override density: (\d+)') { $Matches[1] } else { 'reset' }
                try {
                    & $adb -s $serial shell wm size 1920x1200
                    & $adb -s $serial shell wm density 200
                    Invoke-KivoInstrumentation 'wiki.kivo.app.AdaptiveAcceptanceTest' 'tablet-final' -CapturePrefix 'tablet'
                } finally {
                    & $adb -s $serial shell wm size $oldSize
                    & $adb -s $serial shell wm density $oldDensity
                }
            }
        }
    }
    $screens = Join-Path $logRoot 'screenshots'
    New-Item -ItemType Directory -Force -Path $screens | Out-Null
    & $adb -s $serial pull /sdcard/Android/data/wiki.kivo.app.preview.debug/files/acceptance/. $screens
    Write-Host "已完成 $Mode；截图与日志在 artifacts/character-0.3。"
} finally { Pop-Location }
