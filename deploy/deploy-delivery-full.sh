#!/usr/bin/env bash
# deploy-delivery-full.sh — полный деплой delivery на выделенной машине.
# Секреты и домен: docker-compose.prod.yml (править перед деплоем).
# Git: export GITHUB_TOKEN=... перед запуском (не коммитить в репозиторий).

set -euo pipefail

# --- настройки деплоя ---
GITHUB_TOKEN="${GITHUB_TOKEN:-}"
GITHUB_ORG="llliiya"
BRANCH="main"
DEPLOY_ROOT="/opt/delivery"
DEPLOY_DOMAIN="85.239.58.129.sslip.io"

WORKSPACE="$DEPLOY_ROOT/workspace"

REPOS=(
  miniapp-deploy
  miniapp-account
  miniapp-notification
  miniapp-gateway
  miniapp-delivery
)

clone_url() {
  echo "https://${GITHUB_TOKEN}@github.com/${GITHUB_ORG}/$1.git"
}

echo "===== DEPLOY DELIVERY FULL STARTED ====="

command -v git >/dev/null || { echo "❌ git не найден"; exit 1; }
command -v docker >/dev/null || { echo "❌ docker не найден"; exit 1; }
docker compose version >/dev/null 2>&1 || { echo "❌ docker compose plugin не найден"; exit 1; }

[[ -n "$GITHUB_TOKEN" ]] || { echo "❌ export GITHUB_TOKEN=... перед запуском"; exit 1; }
[[ "$DEPLOY_DOMAIN" != CHANGE_ME* ]] || { echo "❌ Задайте DEPLOY_DOMAIN (как в docker-compose.prod.yml)"; exit 1; }

CERT_DIR="/etc/letsencrypt/live/${DEPLOY_DOMAIN}"
if [[ ! -f "${CERT_DIR}/fullchain.pem" || ! -f "${CERT_DIR}/privkey.pem" ]]; then
  echo "⚠️  Сертификаты не найдены: ${CERT_DIR}"
  echo "   certbot certonly --standalone -d ${DEPLOY_DOMAIN}"
  if [[ -t 0 ]]; then
    read -r -p "Продолжить? [y/N] " ans
    [[ "${ans:-}" =~ ^[yY]$ ]] || exit 1
  else
    exit 1
  fi
fi

if [[ -f "$WORKSPACE/miniapp-delivery/docker-compose.prod.yml" ]]; then
  echo "[0] Остановка предыдущего stack"
  (cd "$WORKSPACE/miniapp-delivery" && docker compose -f docker-compose.prod.yml down) || true
fi

echo "[1] Подготовка $WORKSPACE"
rm -rf "$WORKSPACE"
mkdir -p "$WORKSPACE"
cd "$WORKSPACE"

echo "[2] Клонирование (branch=$BRANCH)"
for repo in "${REPOS[@]}"; do
  echo "  → $repo"
  git clone --depth 1 -b "$BRANCH" "$(clone_url "$repo")" "$repo"
done

echo "[3] Docker compose up"
cd "$WORKSPACE/miniapp-delivery"
docker compose -f docker-compose.prod.yml up -d --build

echo ""
echo "===== DEPLOY DELIVERY FULL FINISHED ====="
echo "https://${DEPLOY_DOMAIN}/api/delivery/health"
