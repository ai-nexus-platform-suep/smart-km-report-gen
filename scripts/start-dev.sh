#!/usr/bin/env bash
# 本地开发一键启动（需 Docker、JDK、Maven、Node 已安装）
set -e

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

echo "==> 启动 Docker 中间件..."
docker-compose up -d

echo "==> 等待 MySQL 就绪..."
sleep 15

echo "==> 编译后端..."
mvn clean install -DskipTests -q

echo "==> 启动后端（后台）..."
cd km-backend
mvn spring-boot:run &
BACKEND_PID=$!
cd "$ROOT"

echo "==> 安装并启动前端..."
cd km-frontend
if [ ! -d node_modules ]; then
  npm install
fi
npm run dev &
FRONTEND_PID=$!
cd "$ROOT"

echo ""
echo "=========================================="
echo "  后端: http://localhost:8091/api/v1/health"
echo "  前端: http://localhost:5173"
echo "  按 Ctrl+C 停止"
echo "=========================================="

trap "kill $BACKEND_PID $FRONTEND_PID 2>/dev/null; exit" INT TERM
wait
