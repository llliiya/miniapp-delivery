# Доборовоз (miniapp-delivery) — навигация для агента

Проект: `c:\valentin\miniapp-delivery`. Стек: Spring Boot + React (Vite), Postgres schema `delivery`, Liquibase.

## Перед любой задачей

1. Открой **релевантный индекс** из `.cursor/index/` (см. таблицу ниже).
2. Ищи **только в указанных папках** — не сканируй домашний каталог и весь `c:\valentin`.
3. Не вызывай `move_agent_to_root`, если путь проекта уже известен — читай файлы напрямую.
4. Для бага: индекс фичи → контроллер → сервис → тест → `docker logs delivery_dev-delivery-backend-1`.

## Индексы

| Задача | Файл |
|--------|------|
| Обзор структуры, слои, пакеты | `.cursor/index/00-overview.md` |
| REST API, эндпоинты | `.cursor/index/01-api-endpoints.md` |
| Роуты UI, страницы, компоненты | `.cursor/index/02-frontend-routes.md` |
| Партнёрская программа | `.cursor/index/03-feature-partner-program.md` |
| Enum'ы backend ↔ frontend | `.cursor/index/04-domain-enums.md` |
| Docker, тесты, логи, миграции | `.cursor/index/05-dev-ops.md` |

## Быстрые пути

```
delivery-backend/src/main/java/ru/kzn/buzanov/delivery/
  api/          — REST-контроллеры
  service/      — бизнес-логика
  repository/   — JPA
  domain/       — сущности и enum
  dto/          — ответы API
  dto/request/  — тела запросов

delivery-frontend/src/
  api/deliveryService.js  — все вызовы API (единая точка)
  routes/                 — роутинг по ролям
  pages/                  — экраны
  components/             — UI-блоки
  utils/                  — лейблы, маппинг enum
```

## Роли в UI

| Роль | Префикс URL | Роуты |
|------|-------------|-------|
| Курьерская служба | `/service/*` | `routes/ServiceRoutes.jsx` |
| Курьер | `/courier/*` | `routes/CourierRoutes.jsx` |
| Объект (ресторан) | `/restaurant/*` | `routes/RestaurantRoutes.jsx` |

В UI «объект» = backend enum `RESTAURANT`, не отдельный `OBJECT`.

## Dev-окружение

- Compose: `c:\valentin\miniapp-delivery\docker-compose.dev.yml` (запуск из `c:\valentin`)
- UI: http://localhost:5173
- API: http://localhost:8080/api/delivery/health
- Postgres: localhost:5430, schema `delivery`
- Контейнер backend: `delivery_dev-delivery-backend-1`

## Правила Cursor

- `.cursor/rules/use-project-index.mdc` — всегда: сначала индекс
- `.cursor/rules/docker-rebuild-after-changes.mdc` — пересборка после правок
