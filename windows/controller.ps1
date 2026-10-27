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
    $GameDir = Read-Host '请输入整合包游戏目录（例如 D:\mc\.minecraft\versions\Bounty Hunter）'
    [System.IO.File]::WriteAllText($configPath, "gameDir=$GameDir`r`n", [System.Text.UTF8Encoding]::new($false))
}

$bridgeConfigPath = Join-Path $GameDir 'config\mc_ai_controller.properties'
if (-not (Test-Path -LiteralPath $bridgeConfigPath)) {
    throw "找不到 $bridgeConfigPath。请先安装模组、启动游戏并进入主菜单一次。"
}

$bridge = Read-SimpleProperties $bridgeConfigPath
$port = [int]$bridge['port']
$token = $bridge['token']
if (-not $port -or -not $token) {
    throw '桥接配置缺少 port 或 token。请删除该配置并重新启动一次游戏。'
}

Write-Host ''
Write-Host 'MC AI Controller 第一版已启动。'
Write-Host '支持：前往坐标 100 70 -200 / 跟随玩家 Mc_kuailewang / 停止任务'
Write-Host 'F8：游戏内紧急停止；解除后请输入：解除急停'
Write-Host '输入 exit 只退出控制器（退出前会先发送停止任务）。'
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
    $command = Read-Host '你'
    if ([string]::IsNullOrWhiteSpace($command)) { continue }
    if ($command -eq 'exit' -or $command -eq 'quit') {
        try { [void](Send-Command '停止任务') } catch {}
        break
    }
    try {
        $response = Send-Command $command
        Write-Host "控制器：$response"
    }
    catch {
        Write-Host "控制器：无法连接 Minecraft：$($_.Exception.Message)" -ForegroundColor Red
        Write-Host '请确认 Minecraft 已启动，并检查 logs\latest.log 中的 “MC AI Controller listening”。'
    }
}
