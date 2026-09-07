param([ValidateSet('Phone','LargeFont','Tablet','Offline')][string]$Mode = 'Phone')
. (Join-Path $PSScriptRoot 'environment.ps1')
Set-KivoBuildEnvironment
$kivoAdb = Join-Path $env:ANDROID_HOME 'platform-tools/adb.exe'
$kivoSerial = 'emulator-5558'
Assert-KivoEmulator $kivoAdb $kivoSerial
$kivoReport = Join-Path $KivoProjectRoot 'artifacts/character-0.3'
New-Item -ItemType Directory -Force -Path $kivoReport | Out-Null

function Invoke-CharacterTests([string]$Class, [string]$Name, [switch]$Offline) {
    $arguments = @('-s',$kivoSerial,'shell','am','instrument','-w','-r','-e','class',$Class,'-e','capturePrefix',$Name)
    if ($Offline) { $arguments += @('-e','expectOffline','true') }
    $arguments += 'wiki.kivo.app.preview.debug.test/androidx.test.runner.AndroidJUnitRunner'
    $output = & $kivoAdb @arguments 2>&1
    $output | Set-Content -LiteralPath (Join-Path $kivoReport "$Name.log") -Encoding utf8
    $joined = $output -join "`n"
    $output | Select-Object -Last 16 | Write-Output
    if ($LASTEXITCODE -ne 0 -or $joined -notmatch 'OK \(\d+ tests?\)' -or $joined -match 'FAILURES!!!|INSTRUMENTATION_FAILED') { throw "角色设备检查失败：$Name.log" }
}

Push-Location $KivoProjectRoot
try {
    & ./gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest --console=plain *> (Join-Path $kivoReport 'matrix-build.log')
    if ($LASTEXITCODE -ne 0) { throw '设备测试包构建失败' }
    & $kivoAdb -s $kivoSerial install -r app/build/outputs/apk/debug/app-debug.apk
    if ($LASTEXITCODE -ne 0) { throw '安装 Debug 失败' }
    & $kivoAdb -s $kivoSerial install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
    if ($LASTEXITCODE -ne 0) { throw '安装测试 APK 失败' }
    $all = 'wiki.kivo.app.CharacterAcceptanceTest,wiki.kivo.app.ContentAlignmentTest,wiki.kivo.app.CharacterExportTest'
    switch ($Mode) {
        'Phone' { Invoke-CharacterTests $all 'phone' }
        'LargeFont' {
            $font = ([string](& $kivoAdb -s $kivoSerial shell settings get system font_scale)).Trim()
            try {
                & $kivoAdb -s $kivoSerial shell settings put system font_scale 2.0
                Invoke-CharacterTests 'wiki.kivo.app.CharacterAcceptanceTest#catalogSearchFiltersAndViewChoicePersistOnReturn,wiki.kivo.app.CharacterAcceptanceTest#sixCharacterTypesExposeNativeDataAndAllTabs' 'font200'
            } finally {
                if ($font -eq 'null') { & $kivoAdb -s $kivoSerial shell settings delete system font_scale } else { & $kivoAdb -s $kivoSerial shell settings put system font_scale $font }
            }
        }
        'Tablet' {
            $size = (& $kivoAdb -s $kivoSerial shell wm size) -join "`n"
            $density = (& $kivoAdb -s $kivoSerial shell wm density) -join "`n"
            $oldSize = if ($size -match 'Override size: (\d+x\d+)') { $Matches[1] } else { 'reset' }
            $oldDensity = if ($density -match 'Override density: (\d+)') { $Matches[1] } else { 'reset' }
            try {
                & $kivoAdb -s $kivoSerial shell wm size 1920x1200
                & $kivoAdb -s $kivoSerial shell wm density 200
                Invoke-CharacterTests 'wiki.kivo.app.CharacterAcceptanceTest' 'tablet'
            } finally {
                & $kivoAdb -s $kivoSerial shell wm size $oldSize
                & $kivoAdb -s $kivoSerial shell wm density $oldDensity
            }
        }
        'Offline' {
            # 先缓存六个角色和四类预览，再仅关闭专用 AVD 的网络；所有设备设置最终恢复。
            Invoke-CharacterTests 'wiki.kivo.app.CharacterAcceptanceTest#sixCharacterTypesExposeNativeDataAndAllTabs,wiki.kivo.app.CharacterAcceptanceTest#allFourNativePreviewTypesLoadAndClose' 'offline-warmup'
            $wifi = ([string](& $kivoAdb -s $kivoSerial shell settings get global wifi_on)).Trim()
            $data = ([string](& $kivoAdb -s $kivoSerial shell settings get global mobile_data)).Trim()
            try {
                & $kivoAdb -s $kivoSerial shell svc wifi disable
                & $kivoAdb -s $kivoSerial shell svc data disable
                Start-Sleep -Seconds 2
                Invoke-CharacterTests 'wiki.kivo.app.CharacterAcceptanceTest#cachedCharacterAndMediaRemainReadableOffline,wiki.kivo.app.CharacterAcceptanceTest#allFourNativePreviewTypesLoadAndClose' 'offline' -Offline
            } finally {
                & $kivoAdb -s $kivoSerial shell svc wifi $(if($wifi -eq '0'){'disable'}else{'enable'})
                & $kivoAdb -s $kivoSerial shell svc data $(if($data -eq '0'){'disable'}else{'enable'})
            }
        }
    }
} finally {
    $screens = Join-Path $kivoReport 'screenshots'
    New-Item -ItemType Directory -Force -Path $screens | Out-Null
    & $kivoAdb -s $kivoSerial pull /sdcard/Android/data/wiki.kivo.app.preview.debug/files/character-acceptance/. $screens
    Pop-Location
}
