# Деплой miniapp-delivery

Конфигурация в compose-файлах (без отдельных `.env`).

| Файл | Среда |
|------|-------|
| `docker-compose.dev.yml` | локально |
| `docker-compose.prod.yml` | prod-сервер |

## Dev

```bash
docker compose -f miniapp-delivery/docker-compose.dev.yml up --build -d
```

UI `http://localhost:5172`, API `http://localhost:8081/api/...`, Postgres `:5430`.

## Prod

1. Правки секретов и домена в `docker-compose.prod.yml`
2. `export GITHUB_TOKEN=...` перед запуском скрипта
3. certbot: `/etc/letsencrypt/live/<DOMAIN>/`
4. `bash deploy/deploy-delivery-full.sh`

Порты на хосте: **80**, **443** (контейнер `delivery-frontend`).

## Nginx и API снаружи

```
Интернет :443
    → delivery-frontend (nginx в контейнере)
        /           → статика SPA
        /api/       → gateway:8080 → delivery-backend / account / notification
```

Файлы: `delivery-frontend/default.conf.template`, `delivery-frontend/Dockerfile`.

Gateway и backend **не** проброшены на хост — API только через `https://<DOMAIN>/api/...`.

На хосте не должно быть другого nginx/apache на 80/443 (`deploy-delivery-full.sh` останавливает их).

## Репозитории для clone

miniapp-delivery, miniapp-deploy, miniapp-account, miniapp-notification, miniapp-gateway.
