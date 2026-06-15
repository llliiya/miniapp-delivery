# miniapp-delivery

Курьерская биржа доставки (отдельно от Помощник ПВЗ).

## Структура

- `delivery-backend` — API, schema `delivery`
- `delivery-frontend` — Mini App (Vite + React)
- `docker-compose.dev.yml` / `docker-compose.prod.yml` — конфигурация стека

## Dev

```bash
docker compose -f miniapp-delivery/docker-compose.dev.yml up --build -d
```

- UI: `http://localhost:5173`
- API: `http://localhost:8080/api/delivery/health`
- Postgres: `:5430`

## Prod

Секреты в `docker-compose.prod.yml`, деплой: `deploy/DEPLOY.md`
