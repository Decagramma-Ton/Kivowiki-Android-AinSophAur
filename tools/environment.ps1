$ErrorActionPreference = 'Stop'
$KivoProjectRoot = Split-Path -Parent $PSScriptRoot

function Find-KivoSdk {
    $candidates = @($env:ANDROID_HOME, $env:ANDROID_SDK_ROOT, (Join-Path $env:LOCALAPPDATA 'Android/Sdk'))
    $localFile = Join-Path $KivoProjectRoot 'local.properties'
    if (Test-Path -LiteralPath $localFile) {
        $line = Get-Content -LiteralPath $localFile | Where-Object { $_ -match '^sdk.dir=' } | Select-Object -First 1
        if ($line) { $candidates += $line.Substring(8).Replace('\:', ':').Replace('\\', '\') }
    }
    foreach ($candidate in $candidates) {
        if ($candidate -and (Test-Path -LiteralPath (Join-Path $candidate 'platform-tools/adb.exe'))) { return [IO.Path]::GetFullPath($candidate) }
    }
    throw '未找到 Android SDK。请先按开发状态/构建与环境.md 安装所列组件。脚本不会自动修改系统。'
}

function Initialize-KivoTools {
    # 英文入口仅指向项目 tools，数据仍在 App项目开发 中；不建立系统级盘符映射。
    $workspace = Split-Path -Parent $KivoProjectRoot
    while ($workspace -match '[^\x00-\x7F]') {
        $parent = Split-Path -Parent $workspace
        if (-not $parent -or $parent -eq $workspace) { throw '找不到可用的英文工具入口目录。' }
        $workspace = $parent
    }
    $alias = Join-Path $workspace '.kivo-tools'
    $target = Join-Path $KivoProjectRoot 'tools'
    if ($alias -match '[^\x00-\x7F]') { throw '模拟器需要英文父路径，请将整个工作区放在英文目录下，再保留其中的 App项目开发 文件夹。' }
    if (Test-Path -LiteralPath $alias) {
        $item = Get-Item -LiteralPath $alias
        # 工程从 App项目开发 移入子目录后，只修复本工作区内、且旧目标已不存在的目录联接。
        # Remove-Item 不加 -Recurse，只删除联接本身；绝不删除目标目录或其他项目的数据。
        if ($item.LinkType -eq 'Junction' -and -not (Test-Path -LiteralPath ([string]$item.Target)) -and
            [IO.Path]::GetFullPath([string]$item.Target).StartsWith($workspace + [IO.Path]::DirectorySeparatorChar)) {
            Remove-Item -LiteralPath $alias -Force
            New-Item -ItemType Junction -Path $alias -Target $target | Out-Null
            $item = Get-Item -LiteralPath $alias
        }
        if ($item.LinkType -ne 'Junction' -or [IO.Path]::GetFullPath([string]$item.Target) -ne [IO.Path]::GetFullPath($target)) {
            throw '.kivo-tools 已存在且不是本工程的工具入口，已停止以免影响其他文件。'
        }
    } else { New-Item -ItemType Junction -Path $alias -Target $target | Out-Null }
    return $alias
}

function Set-KivoBuildEnvironment {
    $sdk = Find-KivoSdk
    $toolRoot = Initialize-KivoTools
    $candidates = @($env:JAVA_HOME, 'C:/Program Files/Java/jdk-21.0.11', 'C:/Program Files/Android/Android Studio/jbr')
    $java = $candidates | Where-Object { $_ -and (Test-Path -LiteralPath (Join-Path $_ 'bin/java.exe')) } | Select-Object -First 1
    if (-not $java) { throw '未找到 JDK 21，请参照构建与环境文档。' }
    $env:JAVA_HOME = $java
    $env:ANDROID_HOME = $sdk
    $env:ANDROID_SDK_ROOT = $sdk
    # 当前 Windows 的 JDK AF_UNIX 管道不可用。不存在的项目路径使 JDK 使用自带 TCP 回环回退。
    # 不创建 uds-unavailable 目录；不关闭防火墙，不修改任何全局 Java 或系统设置。
    $socketOption = '-Djdk.net.unixdomain.tmpdir=' + (Join-Path $toolRoot '.bootstrap/uds-unavailable').Replace('\', '/')
    if ($env:JAVA_TOOL_OPTIONS -notlike '*-Djdk.net.unixdomain.tmpdir=*') { $env:JAVA_TOOL_OPTIONS = ($env:JAVA_TOOL_OPTIONS + ' ' + $socketOption).Trim() }
    $sdkProperty = 'sdk.dir=' + $sdk.Replace('\', '/').Replace(':', '\:')
    Set-Content -LiteralPath (Join-Path $KivoProjectRoot 'local.properties') -Value $sdkProperty -Encoding ascii
}

function Assert-KivoEmulator([string]$Adb, [string]$Serial) {
    if ($Serial -notmatch '^emulator-\d+$') { throw '自动验收仅操作本项目模拟器，真机请按验收说明手动安装。' }
    $name = & $Adb -s $Serial emu avd name 2>$null
    if ($LASTEXITCODE -ne 0 -or $name -notcontains 'Kivo_Archive_Phone') { throw '指定设备不是本项目的验收模拟器，已停止。' }
}


# 从 Android 的版本声明读取安装包版本，避免预览入口和构建产物各维护一份数字。
function Get-KivoVersion {
    $text = Get-Content -Raw -LiteralPath (Join-Path $KivoProjectRoot 'app/build.gradle.kts')
    $match = [regex]::Match($text, 'versionName = "([0-9]+\.[0-9]+\.[0-9]+)"')
    if (-not $match.Success) { throw '无法读取 App 版本声明。' }
    return $match.Groups[1].Value
}
