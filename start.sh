#!/usr/bin/env bash
set -euo pipefail

project_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
cd "$project_dir/deploy"

if ! command -v docker >/dev/null 2>&1; then
  printf '%s\n' '请先安装 Docker Engine 和 Docker Compose 插件。' >&2
  exit 1
fi
if ! docker compose version >/dev/null 2>&1; then
  printf '%s\n' '未找到 Docker Compose 插件，请安装后重试。' >&2
  exit 1
fi
if [[ ! -f .env ]]; then
  printf '%s\n' '请先在项目根目录执行 cp deploy/.env.example deploy/.env，再填写数据库密码、管理员账号密码和 HTTPS 访问地址。' >&2
  exit 1
fi

# Validate without printing the resolved configuration, which contains passwords.
docker compose config --quiet
docker compose up -d --build
docker compose ps
printf '%s\n' '启动命令已完成。请确认容器状态，并将 HTTPS 反向代理转发到 127.0.0.1:8090。' \
  '查看日志：docker compose -f deploy/compose.yml logs --tail=100' \
  '停止服务：docker compose -f deploy/compose.yml stop'
