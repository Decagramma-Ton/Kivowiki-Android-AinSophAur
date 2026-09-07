param([ValidateSet('Debug','Release','Verify')][string]$Target = 'Release')
. (Join-Path $PSScriptRoot 'environment.ps1')
Set-KivoBuildEnvironment
Push-Location $KivoProjectRoot
try {
    # switch 的单项输出会被 PowerShell 解包成字符串，@tasks 随后会按字符展开。
    # 显式保留字符串数组，使 Debug / Release 和多任务 Verify 使用相同的参数边界。
    [string[]]$tasks = @(switch ($Target) {
        'Debug' { @(':app:assembleDebug') }
        'Release' { @(':app:assembleRelease') }
        'Verify' { @(':core:model:test',':core:data:testDebugUnitTest',':core:content:testDebugUnitTest',':core:media:testDebugUnitTest',':feature:character:testDebugUnitTest',':feature:organization:testDebugUnitTest',':app:lintDebug',':app:lintRelease',':app:assembleRelease') }
    })
    # 媒体运行时增加 R8 工作集，发行构建限制并发；只对本次 Gradle 进程生效。
    $limits = if($Target -eq 'Debug'){@()}else{@('--no-parallel','--max-workers=2','-Dorg.gradle.jvmargs=-Xmx4g -Dfile.encoding=COMPAT')}
    & (Join-Path $KivoProjectRoot 'gradlew.bat') @tasks @limits '--console=plain'
    if ($LASTEXITCODE -ne 0) { throw '构建或检查失败；不会更新交付安装包。' }
    New-Item -ItemType Directory -Force -Path artifacts | Out-Null
    $variant = if ($Target -eq 'Debug') { 'debug' } else { 'release' }
    $version = Get-KivoVersion
    $name = if ($variant -eq 'debug') { "kivo-archive-$version-debug.apk" } else { "kivo-archive-$version-preview.apk" }
    Copy-Item -LiteralPath "app/build/outputs/apk/$variant/app-$variant.apk" -Destination (Join-Path 'artifacts' $name)
    $hash = Get-FileHash -LiteralPath (Join-Path 'artifacts' $name) -Algorithm SHA256
    Set-Content -LiteralPath (Join-Path 'artifacts' "$name.sha256") -Value ($hash.Hash.ToLowerInvariant() + '  ' + $name) -Encoding ascii
    Write-Host "已生成 artifacts/$name"
} finally { Pop-Location }
