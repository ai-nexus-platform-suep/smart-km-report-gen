# 本地开发一键启动（Windows PowerShell）
# 需要：Docker Desktop、JDK 8+、Maven、Node.js 18+
$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
Set-Location $Root

Write-Host "==> 启动 Docker 中间件..."
docker compose up -d

Write-Host "==> 等待 MySQL 就绪..."
Start-Sleep -Seconds 15

Write-Host "==> 编译后端..."
mvn clean install -DskipTests -q

Write-Host "==> 启动后端（新窗口）..."
Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd '$Root\km-backend'; mvn spring-boot:run"

Write-Host "==> 启动前端（新窗口）..."
$frontendDir = Join-Path $Root "km-frontend"
if (-not (Test-Path (Join-Path $frontendDir "node_modules"))) {
    Set-Location $frontendDir
    npm install
    Set-Location $Root
}
Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd '$frontendDir'; npm run dev"

Write-Host ""
Write-Host "=========================================="
Write-Host "  后端: http://localhost:8091/api/health"
Write-Host "  前端: http://localhost:5173"
Write-Host "=========================================="
