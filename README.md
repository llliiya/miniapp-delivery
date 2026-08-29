# miniapp-delivery

Курьерская биржа доставки (отдельно от ПВЗ).

Карта платформы: [miniapp-deploy/docs/architecture/delivery.md](../miniapp-deploy/docs/architecture/delivery.md) · [обзор](../miniapp-deploy/docs/architecture/overview.md).

## Структура

- `delivery-backend` — API, schema `delivery`
- `delivery-frontend` — Mini App (Vite + React)
- `docker-compose.dev.yml` / `docker-compose.prod.yml` — стек
- Платформа: `miniapp-account`, `miniapp-notification`, `miniapp-gateway`

## Dev

```bash
docker compose -f miniapp-delivery/docker-compose.dev.yml up --build -d
```

- UI: `http://localhost:5173`
- API: `http://localhost:8080/api/delivery/health`
- Postgres: `:5430`

## Prod

Секреты через env / compose на сервере. Деплой: `deploy/DEPLOY.md`.

**TODO:** захардкоженные токены в `docker-compose.dev.yml` — вынести в `.env`.
