param(
    [Parameter(Mandatory=$true)][string]$GameDir
)

$ErrorActionPreference = 'Stop'
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$backupRoot = Join-Path $GameDir "mc-ai-controller-backup-$stamp"
New-Item -ItemType Directory -Path $backupRoot | Out-Null

foreach ($name in @('mods', 'config')) {
    $source = Join-Path $GameDir $name
    if (Test-Path -LiteralPath $source) {
        Copy-Item -LiteralPath $source -Destination $backupRoot -Recurse
    }
}

$options = Join-Path $GameDir 'options.txt'
if (Test-Path -LiteralPath $options) {
    Copy-Item -LiteralPath $options -Destination $backupRoot
}

Write-Host "备份完成：$backupRoot"
