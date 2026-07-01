# 启动持久 MinerU API（Windows PowerShell）
# 用于并发解析时复用同一个 MinerU 服务，避免每个文档任务启动临时 mineru-api。
param(
    [string]$HostAddress = "127.0.0.1",
    [int]$Port = 8000
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
$ServiceDir = Join-Path $Root "km-ai-service"
$Python = Join-Path $ServiceDir ".venv\Scripts\python.exe"

if (-not (Test-Path -LiteralPath $Python)) {
    throw "Python venv not found: $Python"
}

Set-Location $ServiceDir

$env:MINERU_DEVICE_MODE = if ($env:MINERU_DEVICE_MODE) { $env:MINERU_DEVICE_MODE } else { "cuda" }
$env:MINERU_TORCH_CUDNN_ENABLED = if ($env:MINERU_TORCH_CUDNN_ENABLED) { $env:MINERU_TORCH_CUDNN_ENABLED } else { "false" }

Write-Host "Starting MinerU API at http://$HostAddress`:$Port"
Write-Host "MINERU_DEVICE_MODE=$env:MINERU_DEVICE_MODE"
Write-Host "MINERU_TORCH_CUDNN_ENABLED=$env:MINERU_TORCH_CUDNN_ENABLED"

& $Python -c "import os, sys; import torch; torch.backends.cudnn.enabled = os.getenv('MINERU_TORCH_CUDNN_ENABLED', 'true').lower() in {'1','true','yes','y','on'}; from mineru.cli.fast_api import main; main(args=sys.argv[1:], prog_name='mineru-api')" --host $HostAddress --port $Port
