$ErrorActionPreference = 'Stop'
# 只执行构建脚本的任务数组赋值，不启动 Gradle，也不复制交付包。
# 通过语法树取原语句，确保回归覆盖生产脚本，而不是在测试里复制一份任务列表实现。
$source = Join-Path $PSScriptRoot 'build.ps1'
$parseTokens = $null
$parseErrors = $null
$ast = [Management.Automation.Language.Parser]::ParseFile($source, [ref]$parseTokens, [ref]$parseErrors)
if ($parseErrors.Count -gt 0) { throw '构建脚本有语法错误。' }
$assignment = $ast.Find({ param($node)
    $node -is [Management.Automation.Language.AssignmentStatementAst] -and
    $node.Left.Extent.Text -match '\$tasks$'
}, $true)
if (-not $assignment) { throw '未找到任务参数定义。' }
foreach ($Target in @('Debug', 'Release', 'Verify')) {
    $tasks = $null
    . ([scriptblock]::Create($assignment.Extent.Text))
    if ($tasks -isnot [string[]]) { throw "$Target 的任务参数未保留为字符串数组，会被按字符展开。" }
    $expectedCount = if ($Target -eq 'Verify') { 9 } else { 1 }
    if ($tasks.Count -ne $expectedCount) { throw "$Target 的任务数量不正确。" }
    foreach ($task in $tasks) {
        if ($task -notmatch '^(:[A-Za-z][A-Za-z0-9]*){2,}$') { throw "无效任务路径：$task" }
    }
    Write-Host "$Target 参数校验通过：$($tasks -join ', ')"
}
