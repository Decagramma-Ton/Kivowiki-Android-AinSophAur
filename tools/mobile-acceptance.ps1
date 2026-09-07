param([switch]$SkipBuild, [ValidateSet('All','LargeFont')][string]$Mode = 'All')
. (Join-Path $PSScriptRoot 'environment.ps1')
Set-KivoBuildEnvironment
$kivoAdb = Join-Path $env:ANDROID_HOME 'platform-tools/adb.exe'
$kivoSerial = 'emulator-5558'
Assert-KivoEmulator $kivoAdb $kivoSerial
$kivoEvidence = Join-Path $KivoProjectRoot 'artifacts/mobile-0.4.1'
New-Item -ItemType Directory -Force $kivoEvidence | Out-Null
New-Item -ItemType Directory -Force (Join-Path $kivoEvidence 'character-acceptance') | Out-Null
function Invoke-MobileTest([string]$Classes, [string]$Name) {
    $result = & $kivoAdb -s $kivoSerial shell am instrument -w -r -e class $Classes -e capturePrefix phone wiki.kivo.app.preview.debug.test/androidx.test.runner.AndroidJUnitRunner 2>&1
    $result | Set-Content -LiteralPath (Join-Path $kivoEvidence "$Name.log") -Encoding utf8
    $joined = $result -join "`n"
    $result | Select-Object -Last 12 | Write-Output
    if ($LASTEXITCODE -ne 0 -or $joined -notmatch 'OK \(\d+ tests?\)' -or $joined -match 'FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed') { throw "设备回归失败：$Name.log" }
}
Push-Location $KivoProjectRoot
try {
    if (-not $SkipBuild) {
        & ./gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest --no-parallel --max-workers=2 --console=plain *> (Join-Path $kivoEvidence 'device-build.log')
        if ($LASTEXITCODE -ne 0) { throw '设备包构建失败' }
        & $kivoAdb -s $kivoSerial install -r app/build/outputs/apk/debug/app-debug.apk
        if ($LASTEXITCODE -ne 0) { throw '安装 Debug 失败' }
        & $kivoAdb -s $kivoSerial install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
        if ($LASTEXITCODE -ne 0) { throw '安装测试包失败' }
    }
    if ($Mode -eq 'All') {
    Invoke-MobileTest 'wiki.kivo.app.MobileUiRegressionTest,wiki.kivo.app.ContentAlignmentTest,wiki.kivo.app.CharacterExportTest,wiki.kivo.app.ThumbnailReliabilityTest,wiki.kivo.app.CharacterAcceptanceTest#allFourNativePreviewTypesLoadAndClose,wiki.kivo.app.CharacterAcceptanceTest#voicePlaybackStopsWhenSwitchingCategory' 'phone'
    }
    # 窄屏仅断言仍可操作与跟手滚动，不强迫短字段保持三列。
    $sizeInfo = (& $kivoAdb -s $kivoSerial shell wm size) -join "`n"
    $densityInfo = (& $kivoAdb -s $kivoSerial shell wm density) -join "`n"
    $oldSize = if ($sizeInfo -match 'Override size: (\d+x\d+)') {$Matches[1]} else {'reset'}
    $oldDensity = if ($densityInfo -match 'Override density: (\d+)') {$Matches[1]} else {'reset'}
    $oldFont = ([string](& $kivoAdb -s $kivoSerial shell settings get system font_scale)).Trim()
    try {
        if ($Mode -eq 'All') {
        & $kivoAdb -s $kivoSerial shell wm size 720x1600
        & $kivoAdb -s $kivoSerial shell wm density 360
        Invoke-MobileTest 'wiki.kivo.app.MobileUiRegressionTest#pagerTracksHeldFingerAndInfoCanLeaveTheBottomWithSmallDrags' 'narrow320'
        }
        & $kivoAdb -s $kivoSerial shell wm size $oldSize
        & $kivoAdb -s $kivoSerial shell wm density $oldDensity
        & $kivoAdb -s $kivoSerial shell settings put system font_scale 2.0
        Invoke-MobileTest 'wiki.kivo.app.OrganizationAcceptanceTest#catalogSearchAndReturnRetainQuery,wiki.kivo.app.OrganizationAcceptanceTest#settingsDisableSwipePersistAfterRecreationAndKeepTapAvailable' 'font200'
    } finally {
        & $kivoAdb -s $kivoSerial shell wm size $oldSize
        & $kivoAdb -s $kivoSerial shell wm density $oldDensity
        if ($oldFont -eq 'null') { & $kivoAdb -s $kivoSerial shell settings delete system font_scale }
        else { & $kivoAdb -s $kivoSerial shell settings put system font_scale $oldFont }
    }
    New-Item -ItemType Directory -Force (Join-Path $kivoEvidence 'screenshots') | Out-Null
    & $kivoAdb -s $kivoSerial pull /sdcard/Android/data/wiki.kivo.app.preview.debug/files/mobile-regression/. (Join-Path $kivoEvidence 'screenshots')
    if ($LASTEXITCODE -ne 0) { throw '截图归档失败' }
    foreach ($id in @(1562,467,440,87)) {
        & $kivoAdb -s $kivoSerial pull "/sdcard/Android/data/wiki.kivo.app.preview.debug/files/character-acceptance/phone-full-$id.png" (Join-Path $kivoEvidence "character-acceptance/phone-full-$id.png")
    }
    foreach ($id in @(1562,440)) {
        & $kivoAdb -s $kivoSerial pull "/sdcard/Android/data/wiki.kivo.app.preview.debug/files/character-acceptance/phone-full-$id.mp4" (Join-Path $kivoEvidence "character-acceptance/phone-full-$id.mp4")
    }
} finally { Pop-Location }
