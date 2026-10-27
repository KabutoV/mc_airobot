param(
    [string]$GameDir
)

$ErrorActionPreference = 'Stop'
[Console]::InputEncoding = [System.Text.UTF8Encoding]::new($false)
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)
$configPath = Join-Path $PSScriptRoot 'controller.properties'

function Read-SimpleProperties([string]$Path) {
    $result = @{}
    if (Test-Path -LiteralPath $Path) {
        foreach ($line in Get-Content -LiteralPath $Path -Encoding UTF8) {
            if ($line -match '^\s*([^#!][^=]*)=(.*)$') {
                $result[$matches[1].Trim()] = $matches[2].Trim()
            }
        }
    }
    return $result
}

if (-not $GameDir) {
    $localConfig = Read-SimpleProperties $configPath
    $GameDir = $localConfig['gameDir']
}

if (-not $GameDir) {
    $GameDir = Read-Host 'Enter the Minecraft game directory'
    [System.IO.File]::WriteAllText($configPath, "gameDir=$GameDir`r`n", [System.Text.UTF8Encoding]::new($false))
}

$bridgeConfigPath = Join-Path $GameDir 'config\mc_ai_controller.properties'
if (-not (Test-Path -LiteralPath $bridgeConfigPath)) {
    throw "Cannot find $bridgeConfigPath. Start Minecraft with the bridge mod once first."
}

$bridge = Read-SimpleProperties $bridgeConfigPath
$port = [int]$bridge['port']
$token = $bridge['token']
if (-not $port -or -not $token) {
    throw 'Bridge config is missing port or token. Delete it and restart Minecraft once.'
}

Write-Host ''
Write-Host 'MC AI Controller v0.1.0 is ready.'
Write-Host 'Examples: go to 100 70 -200 / follow player Mc_kuailewang / stop'
Write-Host 'Chinese commands are also supported.'
Write-Host 'F8: emergency stop. Type resume or unlock to clear the emergency lock.'
Write-Host 'Type exit to stop the task and close this controller.'
Write-Host ''

function Send-Command([string]$Command) {
    $client = [System.Net.Sockets.TcpClient]::new()
    try {
        $client.Connect('127.0.0.1', $port)
        $stream = $client.GetStream()
        $writer = [System.IO.StreamWriter]::new($stream, [System.Text.UTF8Encoding]::new($false), 1024, $true)
        $reader = [System.IO.StreamReader]::new($stream, [System.Text.UTF8Encoding]::new($false), $false, 1024, $true)
        $writer.NewLine = "`n"
        $writer.WriteLine("$token`t$Command")
        $writer.Flush()
        return $reader.ReadLine()
    }
    finally {
        $client.Dispose()
    }
}

while ($true) {
    $command = Read-Host 'You'
    if ([string]::IsNullOrWhiteSpace($command)) { continue }
    if ($command -eq 'exit' -or $command -eq 'quit') {
        try { [void](Send-Command 'stop') } catch {}
        break
    }
    try {
        $response = Send-Command $command
        Write-Host "Controller: $response"
    }
    catch {
        Write-Host "Controller: Cannot connect to Minecraft: $($_.Exception.Message)" -ForegroundColor Red
        Write-Host 'Keep Minecraft running and check latest.log for MC AI Controller listening.'
    }
}
