# miniapp-delivery

Отдельный продукт курьерской биржи доставки. Не входит в «Помощник ПВЗ».

## Структура

- `delivery-backend` — доменный API, schema PostgreSQL `delivery`
- `delivery-frontend` — Mini App (Vite + React)
- `docs/` — архитектура и договорённости

## Локальный запуск (через общий dev-стек)

Из корня репозитория (`c:\valentin`):

```bash
cd miniapp-deploy
mvn -q -DskipTests package
docker compose --env-file deploy/dev-stack.env -f docker-compose.dev.yml up --build
```

- ПВЗ UI: `http://localhost:5173`
- Delivery UI: `http://localhost:5174`
- API gateway: `http://localhost:8080/api/...`
- Delivery health: `http://localhost:8080/api/delivery/health`

Только delivery-стек (`miniapp-delivery/docker-compose.dev.yml`): Postgres на `:5430`, база **`delivery`** (схемы `account`, `delivery`, `notification`).

## Переменные

См. `miniapp-deploy/deploy/dev-stack.env.example`.

## Telegram Mini App (HTTPS через arenda.web)

Delivery работает на **`https://arenda.web.buzanov-vo.ru`** (тот же DNS/SSL, что аренда).

**BotFather Web App URL:** `https://arenda.web.buzanov-vo.ru`

### На сервере (nginx)

Замените блок `arenda.web` на конфиг из `miniapp-deploy/deploy/host-nginx-delivery-buzanov-vo.conf`  
(UI → `10.8.0.17:5174`, `/api/` → `10.8.0.17:8080`), затем:

```bash
nginx -t && systemctl reload nginx
```

### Локально

```bash
cd miniapp-deploy
mvn -q -DskipTests package
docker compose --env-file deploy/dev-stack.env \
  -f ../miniapp-delivery/docker-compose.dev.yml up --build -d
```

Проверка: `https://arenda.web.buzanov-vo.ru/api/delivery/health`

Подробнее: `miniapp-deploy/docs/telegram-miniapp-infrastructure.md`
