# 后端编译与单测（本地 / CI 等价命令）
$ErrorActionPreference = "Stop"
Set-Location (Split-Path -Parent $PSScriptRoot)
mvn -B clean verify
