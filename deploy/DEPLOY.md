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

1. На сервере: **JDK 21**, **Maven 3.9+**, Docker Compose
2. Все значения в `docker-compose.prod.yml` (домен: `217.149.22.212.sslip.io`)
3. `export GITHUB_TOKEN=...` перед запуском скрипта
4. certbot: `/etc/letsencrypt/live/217.149.22.212.sslip.io/`
5. `bash deploy/deploy-delivery-full.sh`

Скрипт собирает Java-сервисы через Maven на хосте (`~/.m2` кэш), в Docker кладёт только готовые JAR (`docker/Dockerfile.*.jar`). Frontend по-прежнему собирается в контейнере.

Порты на хосте: **80**, **443** (контейнер `delivery-frontend`).

Старый `prod-stack.env` на сервере **не используется** — можно удалить или переименовать.

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
