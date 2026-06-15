# Деплой miniapp-delivery

| Файл | Среда |
|------|-------|
| `docker-compose.dev.yml` | локально |
| `docker-compose.prod.yml` | prod-сервер |

## Dev

```bash
docker compose -f miniapp-delivery/docker-compose.dev.yml up --build -d
```

UI `http://localhost:5173`, API `http://localhost:8080/api/...`, Postgres `:5430`.

## Prod

1. Секреты и домен: `miniapp-deploy/deploy/prod-stack.env` на сервере (`DELIVERY_TLS_DOMAIN=85.239.58.129.sslip.io`)
2. `export GITHUB_TOKEN=...` перед запуском скрипта
3. certbot: `/etc/letsencrypt/live/85.239.58.129.sslip.io/`
4. `bash deploy/deploy-delivery-full.sh`

Порты на хосте: **80**, **443** (контейнер `delivery-frontend`).

## Nginx и API снаружи

```
Интернет :443
    → delivery-frontend (nginx в контейнере)
        /           → статика SPA
        /api/       → gateway:8080 → delivery-backend / account / notification
```

Файлы: `delivery-frontend/nginx.prod.conf` (volume в compose), `delivery-frontend/Dockerfile`.

Gateway и backend **не** проброшены на хост — API только через `https://<DOMAIN>/api/...`.

На хосте не должно быть другого nginx/apache на 80/443 (`deploy-delivery-full.sh` останавливает их).

## Репозитории для clone

miniapp-delivery, miniapp-deploy, miniapp-account, miniapp-notification, miniapp-gateway.
