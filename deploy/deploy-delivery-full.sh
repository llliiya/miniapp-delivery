#!/usr/bin/env bash
# deploy-delivery-full.sh — полный деплой delivery на выделенной машине.
# Конфиг и секреты: miniapp-delivery/docker-compose.prod.yml
# Git: export GITHUB_TOKEN=... перед запуском (не коммитить в репозиторий).

set -euo pipefail

GITHUB_TOKEN=""
GITHUB_ORG="llliiya"
BRANCH="main"
DEPLOY_ROOT="/opt/delivery"
WORKSPACE="$DEPLOY_ROOT/workspace"
COMPOSE_FILE="$WORKSPACE/miniapp-delivery/docker-compose.prod.yml"

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

load_deploy_domain_from_compose() {
  local compose_file="$1"
  DEPLOY_DOMAIN="$(grep -E '^\s+DOMAIN:' "$compose_file" | head -1 | sed -E 's/^[[:space:]]+DOMAIN:[[:space:]]*//' | tr -d '\r')"
}

echo "===== DEPLOY DELIVERY FULL STARTED ====="

command -v git >/dev/null || { echo "❌ git не найден"; exit 1; }
command -v docker >/dev/null || { echo "❌ docker не найден"; exit 1; }
docker compose version >/dev/null 2>&1 || { echo "❌ docker compose plugin не найден"; exit 1; }

[[ -n "$GITHUB_TOKEN" ]] || { echo "❌ export GITHUB_TOKEN=... перед запуском"; exit 1; }

if [[ -f "$COMPOSE_FILE" ]]; then
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

[[ -f "$COMPOSE_FILE" ]] || { echo "❌ Не найден $COMPOSE_FILE"; exit 1; }

load_deploy_domain_from_compose "$COMPOSE_FILE"
[[ -n "${DEPLOY_DOMAIN:-}" ]] || {
  echo "❌ DOMAIN не найден в docker-compose.prod.yml (delivery-frontend.environment.DOMAIN)"
  exit 1
}
echo "[2a] Deploy domain (TLS): $DEPLOY_DOMAIN"

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

# delivery-frontend (nginx) занимает :80 и :443 на хосте — освобождаем от system nginx/apache
if command -v systemctl >/dev/null; then
  for svc in nginx apache2; do
    if systemctl is-active --quiet "$svc" 2>/dev/null; then
      echo "[2b] Останавливаем $svc (конфликт портов 80/443 с delivery-frontend)"
      systemctl stop "$svc" || true
    fi
  done
fi

echo "[3] Docker compose up"
cd "$WORKSPACE/miniapp-delivery"
docker compose -f docker-compose.prod.yml up -d --build

echo ""
echo "===== DEPLOY DELIVERY FULL FINISHED ====="
echo "https://${DEPLOY_DOMAIN}/api/delivery/health"
