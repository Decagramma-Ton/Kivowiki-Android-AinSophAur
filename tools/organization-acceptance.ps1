param([ValidateSet('Phone','LargeFont','Tablet','Offline')][string]$Mode = 'Phone', [switch]$SkipBuild, [string]$Case = '', [switch]$SkipPrime)
. (Join-Path $PSScriptRoot 'environment.ps1')
Set-KivoBuildEnvironment
$kivoAdb = Join-Path $env:ANDROID_HOME 'platform-tools/adb.exe'
$kivoSerial = 'emulator-5558'
Assert-KivoEmulator $kivoAdb $kivoSerial
$kivoReport = Join-Path $KivoProjectRoot 'artifacts/organization-0.4'
New-Item -ItemType Directory -Force $kivoReport | Out-Null
function Invoke-OrganizationTest([string]$Class, [string]$Name, [switch]$Offline) {
    $testArgs = @('-s',$kivoSerial,'shell','am','instrument','-w','-r','-e','class',$Class,'-e','capturePrefix',$Name)
    if ($Offline) { $testArgs += @('-e','expectOffline','true') }
    $testArgs += 'wiki.kivo.app.preview.debug.test/androidx.test.runner.AndroidJUnitRunner'
    $result = & $kivoAdb @testArgs 2>&1
    $result | Set-Content -LiteralPath (Join-Path $kivoReport "$Name.log") -Encoding utf8
    $joined = $result -join "`n"
    $result | Select-Object -Last 12 | Write-Output
    if ($LASTEXITCODE -ne 0 -or $joined -notmatch 'OK \(\d+ tests?\)' -or $joined -match 'FAILURES!!!|INSTRUMENTATION_FAILED|Process crashed') {
        throw "设备测试失败：$Name.log"
    }
}
Push-Location $KivoProjectRoot
try {
    # 仅在当前源码的 Debug 与测试 APK 已构建并安装时使用 SkipBuild。
    if (-not $SkipBuild) {
        # Windows PowerShell 5.1 将重定向的 JVM 启动提示包装为 NativeCommandError。
        # 仅本次原生命令按退出码判断，恢复后仍对文件/脚本错误立即停止。
        $oldPreference = $ErrorActionPreference
        try {
            $ErrorActionPreference = 'Continue'
            & ./gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest --no-parallel --max-workers=2 --console=plain *> (Join-Path $kivoReport 'device-build.log')
            $buildExit = $LASTEXITCODE
        } finally { $ErrorActionPreference = $oldPreference }
        if ($buildExit -ne 0) { throw '设备测试包构建失败' }
        & $kivoAdb -s $kivoSerial install -r app/build/outputs/apk/debug/app-debug.apk
        if ($LASTEXITCODE -ne 0) { throw '安装 Debug 失败' }
        & $kivoAdb -s $kivoSerial install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
        if ($LASTEXITCODE -ne 0) { throw '安装测试 APK 失败' }
    }
    $class = 'wiki.kivo.app.OrganizationAcceptanceTest'
    if ($Case) { $class += '#' + $Case }
    switch ($Mode) {
        'Phone' { Invoke-OrganizationTest "$class,wiki.kivo.app.MermaidAcceptanceTest" 'phone' }
        'LargeFont' {
            $oldFont = ([string](& $kivoAdb -s $kivoSerial shell settings get system font_scale)).Trim()
            try {
                & $kivoAdb -s $kivoSerial shell settings put system font_scale 2.0
                Invoke-OrganizationTest "$class#catalogSearchAndReturnRetainQuery,$class#abydosMapLandmarksRelationsAndMembersAreNavigable,$class#settingsDisableSwipePersistAfterRecreationAndKeepTapAvailable" 'font200'
            } finally {
                if ($oldFont -eq 'null') { & $kivoAdb -s $kivoSerial shell settings delete system font_scale }
                else { & $kivoAdb -s $kivoSerial shell settings put system font_scale $oldFont }
            }
        }
        'Tablet' {
            $sizeInfo = (& $kivoAdb -s $kivoSerial shell wm size) -join "`n"
            $densityInfo = (& $kivoAdb -s $kivoSerial shell wm density) -join "`n"
            $oldSize = if ($sizeInfo -match 'Override size: (\d+x\d+)') {$Matches[1]} else {'reset'}
            $oldDensity = if ($densityInfo -match 'Override density: (\d+)') {$Matches[1]} else {'reset'}
            try {
                & $kivoAdb -s $kivoSerial shell wm size 1920x1200
                & $kivoAdb -s $kivoSerial shell wm density 200
                Invoke-OrganizationTest $class $(if ($Case) { 'tablet-targeted' } else { 'tablet' })
            } finally {
                & $kivoAdb -s $kivoSerial shell wm size $oldSize
                & $kivoAdb -s $kivoSerial shell wm density $oldDensity
            }
        }
        'Offline' {
            # 先读取待验收的公开资料；只改变本项目模拟器的网络，finally 恢复原状态。
            if (-not $SkipPrime) { Invoke-OrganizationTest $class 'offline-prime' }
            $oldWifi = ([string](& $kivoAdb -s $kivoSerial shell settings get global wifi_on)).Trim()
            $oldMobile = ([string](& $kivoAdb -s $kivoSerial shell settings get global mobile_data)).Trim()
            try {
                & $kivoAdb -s $kivoSerial shell svc wifi disable
                & $kivoAdb -s $kivoSerial shell svc data disable
                Invoke-OrganizationTest "$class,wiki.kivo.app.MermaidAcceptanceTest" 'offline' -Offline
            } finally {
                if ($oldWifi -ne '0') { & $kivoAdb -s $kivoSerial shell svc wifi enable }
                if ($oldMobile -ne '0') { & $kivoAdb -s $kivoSerial shell svc data enable }
            }
        }
    }
    & $kivoAdb -s $kivoSerial pull /sdcard/Android/data/wiki.kivo.app.preview.debug/files/organization-acceptance/. $kivoReport
} finally { Pop-Location }
