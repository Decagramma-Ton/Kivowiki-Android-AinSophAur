. (Join-Path $PSScriptRoot 'environment.ps1')
$adb = Join-Path (Find-KivoSdk) 'platform-tools/adb.exe'
$serial = 'emulator-5558'
Assert-KivoEmulator $adb $serial
& $adb -s $serial emu kill
Write-Host '已关闭古书馆专用模拟器，应用数据会保留。'
