. (Join-Path $PSScriptRoot 'environment.ps1')
Write-Host "工程：$KivoProjectRoot"
$sdk = Find-KivoSdk
Write-Host "Android SDK：$sdk"
foreach ($component in @('platforms/android-37.0/android.jar','build-tools/37.0.0/aapt.exe','platform-tools/adb.exe','system-images/android-36.1/google_apis_playstore/x86_64/package.xml')) {
    $found = Test-Path -LiteralPath (Join-Path $sdk $component)
    Write-Host "$component : $found"
}
$emulator = Join-Path $PSScriptRoot 'android-runtime/emulator/emulator.exe'
if (Test-Path -LiteralPath $emulator) { & $emulator -version | Select-Object -First 1 }
& (Join-Path $sdk 'platform-tools/adb.exe') devices
Write-Host '以上只检查与本工程相关的工具和连接设备。'
