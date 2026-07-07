# 00 — Обзор проекта

## Назначение

Mini App «Доборовоз» — курьерская биржа доставки. Три интерфейса: служба, курьер, объект (ресторан).

## Модули репозитория

| Путь | Назначение |
|------|------------|
| `delivery-backend/` | Spring Boot API, порт 9081 внутри Docker |
| `delivery-frontend/` | Vite + React, Telegram/MAX mini app |
| `docker-compose.dev.yml` | Dev-стек (db, account, notification, backend, gateway, frontend) |
| `docker/init-schemas.sql` | Создание schema `delivery` при первом старте PG |
| `scripts/rebuild-dev-backend.ps1` | Быстрая пересборка backend |

## Backend: слои

```
api/*Controller.java     → HTTP
service/*Service.java   → логика
repository/*Repository  → JPA
domain/*                → @Entity, enum
dto/*                   → response
dto/request/*           → request body
```

Базовый пакет: `ru.kzn.buzanov.delivery`

## Frontend: слои

```
routes/*Routes.jsx      → React Router по роли
pages/**                → экраны
components/**           → переиспользуемые блоки
api/deliveryService.js  → все REST-вызовы
api/http.js             → fetch + auth headers
context/AuthContext.jsx → сессия, роль
utils/displayLabels.js  → человекочитаемые enum
index.css               → глобальные стили (в т.ч. partner-program-*)
```

## Внешние сервисы (monorepo `c:\valentin`)

| Сервис | Назначение |
|--------|------------|
| `miniapp-account` | JWT, пользователи, provisioning |
| `miniapp-notification` | Telegram/MAX уведомления |
| gateway (:8080) | Прокси `/api/delivery` → backend |

## БД

- Postgres, schema `delivery`
- Миграции: Liquibase `delivery-backend/src/main/resources/db/changelog/`
- `ddl-auto: validate` — схема только через Liquibase

## Тесты backend

`delivery-backend/src/test/java/ru/kzn/buzanov/delivery/`

Запуск одного теста:
```powershell
cd c:\valentin\miniapp-delivery\delivery-backend
mvn -Dtest=ClassName test
```
