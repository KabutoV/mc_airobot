$ErrorActionPreference = 'Stop'
$expectedSha1 = '36915642c33c21c9225f89e0c1c53804c4231c72'
$destination = Join-Path (Split-Path $PSScriptRoot -Parent) 'libs\baritone-api-forge-1.10.5.jar'
$url = 'https://github.com/cabaletta/baritone/releases/download/v1.10.5/baritone-api-forge-1.10.5.jar'

New-Item -ItemType Directory -Path (Split-Path $destination -Parent) -Force | Out-Null
Invoke-WebRequest -UseBasicParsing -Uri $url -OutFile $destination
$actualSha1 = (Get-FileHash -LiteralPath $destination -Algorithm SHA1).Hash.ToLowerInvariant()
if ($actualSha1 -ne $expectedSha1) {
    Remove-Item -LiteralPath $destination
    throw "Baritone checksum failed. Expected $expectedSha1, actual $actualSha1. The file was deleted."
}
Write-Host "Official Baritone file downloaded and verified: $destination"
