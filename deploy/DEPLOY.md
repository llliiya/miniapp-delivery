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

UI `http://localhost:5173`, API `http://localhost:8080/api/...`, Postgres `:5430`.

## Prod

1. Секреты и домен: `miniapp-deploy/deploy/prod-stack.env` на сервере
2. `export GITHUB_TOKEN=...` перед запуском скрипта
3. TLS на хосте: nginx + certbot (`miniapp-deploy/deploy/nginx-delivery.buzanov-vo.ru.conf`)
4. `bash deploy/deploy-delivery-full.sh`

Порты Docker на хосте: **gateway :8080**, **frontend :5172** (заданы в `docker-compose.prod.yml`).

## Nginx и API снаружи

```
Интернет :443
    → nginx на хосте (TLS)
        /api/  → 127.0.0.1:8080 (gateway)
        /      → 127.0.0.1:5172 (delivery-frontend, статика SPA)
```

Gateway и backend **не** проброшены на 443 — API снаружи через `https://<DOMAIN>/api/...` (прокси host nginx).

## Репозитории для clone

miniapp-delivery, miniapp-deploy, miniapp-account, miniapp-notification, miniapp-gateway.
