#!/usr/bin/env bash
# deploy-delivery-full.sh — полный деплой delivery на выделенной машине.
# Секреты и домен: miniapp-deploy/deploy/prod-stack.env (на сервере, не в git).
# Git: export GITHUB_TOKEN=... перед запуском (не коммитить в репозиторий).

set -euo pipefail

# --- настройки деплоя ---
GITHUB_TOKEN="${GITHUB_TOKEN:-}"
GITHUB_ORG="llliiya"
BRANCH="main"
DEPLOY_ROOT="/opt/delivery"
# Переопределите при необходимости: ENV_FILE=/opt/delivery/prod-stack.env
ENV_FILE="${ENV_FILE:-}"

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

resolve_env_file() {
  if [[ -n "$ENV_FILE" && -f "$ENV_FILE" ]]; then
    echo "$ENV_FILE"
    return
  fi
  local candidates=(
    "$DEPLOY_ROOT/prod-stack.env"
    "$WORKSPACE/miniapp-deploy/deploy/prod-stack.env"
  )
  for candidate in "${candidates[@]}"; do
    if [[ -f "$candidate" ]]; then
      echo "$candidate"
      return
    fi
  done
  echo ""
}

load_deploy_domain_from_env() {
  local env_file="$1"
  # shellcheck disable=SC1090
  set -a
  source "$env_file"
  set +a

  if [[ -n "${DELIVERY_TLS_DOMAIN:-}" ]]; then
    DEPLOY_DOMAIN="$DELIVERY_TLS_DOMAIN"
    return
  fi
  if [[ -n "${FRONTEND_DELIVERY_URL:-}" ]]; then
    DEPLOY_DOMAIN="${FRONTEND_DELIVERY_URL#https://}"
    DEPLOY_DOMAIN="${DEPLOY_DOMAIN#http://}"
    DEPLOY_DOMAIN="${DEPLOY_DOMAIN%%/*}"
    return
  fi
  DEPLOY_DOMAIN=""
}

echo "===== DEPLOY DELIVERY FULL STARTED ====="

command -v git >/dev/null || { echo "❌ git не найден"; exit 1; }
command -v docker >/dev/null || { echo "❌ docker не найден"; exit 1; }
docker compose version >/dev/null 2>&1 || { echo "❌ docker compose plugin не найден"; exit 1; }

[[ -n "$GITHUB_TOKEN" ]] || { echo "❌ export GITHUB_TOKEN=... перед запуском"; exit 1; }

if [[ -f "$WORKSPACE/miniapp-delivery/docker-compose.prod.yml" ]]; then
  echo "[0] Остановка предыдущего stack"
  PREV_ENV="$(resolve_env_file)"
  if [[ -n "$PREV_ENV" ]]; then
    (cd "$WORKSPACE/miniapp-delivery" && docker compose --env-file "$PREV_ENV" -f docker-compose.prod.yml down) || true
  else
    (cd "$WORKSPACE/miniapp-delivery" && docker compose -f docker-compose.prod.yml down) || true
  fi
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

RESOLVED_ENV_FILE="$(resolve_env_file)"
[[ -n "$RESOLVED_ENV_FILE" ]] || {
  echo "❌ prod-stack.env не найден. Скопируйте на сервер один из путей:"
  echo "   $DEPLOY_ROOT/prod-stack.env"
  echo "   $WORKSPACE/miniapp-deploy/deploy/prod-stack.env"
  exit 1
}
echo "[2a] Env file: $RESOLVED_ENV_FILE"

load_deploy_domain_from_env "$RESOLVED_ENV_FILE"
[[ -n "${DEPLOY_DOMAIN:-}" ]] || {
  echo "❌ Задайте DELIVERY_TLS_DOMAIN или FRONTEND_DELIVERY_URL в $RESOLVED_ENV_FILE"
  exit 1
}
echo "[2b] Deploy domain (TLS): $DEPLOY_DOMAIN"

CERT_DIR="/etc/letsencrypt/live/${DEPLOY_DOMAIN}"
if [[ ! -f "${CERT_DIR}/fullchain.pem" || ! -f "${CERT_DIR}/privkey.pem" ]]; then
  echo "⚠️  Сертификаты не найдены: ${CERT_DIR}"
  echo "   certbot certonly --webroot -w /var/www/certbot -d ${DEPLOY_DOMAIN}"
  if [[ -t 0 ]]; then
    read -r -p "Продолжить? [y/N] " ans
    [[ "${ans:-}" =~ ^[yY]$ ]] || exit 1
  else
    exit 1
  fi
fi

echo "[3] Docker compose up (--env-file $RESOLVED_ENV_FILE)"
cd "$WORKSPACE/miniapp-delivery"
docker compose --env-file "$RESOLVED_ENV_FILE" -f docker-compose.prod.yml up -d --build

echo ""
echo "===== DEPLOY DELIVERY FULL FINISHED ====="
echo "https://${DEPLOY_DOMAIN}/api/delivery/health"
