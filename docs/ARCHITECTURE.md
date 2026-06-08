# Архитектура miniapp-delivery

Согласованные решения зафиксированы в ТЗ и в реализации Этапа 1.

## Компоненты

| Компонент | Назначение |
|-----------|------------|
| `delivery-backend` | API, schema `delivery`, JWT, Liquibase |
| `delivery-frontend` | Mini App UI (курьер / ресторан / служба) |
| `miniapp-account` | Пользователи, JWT |
| `miniapp-gateway` | `/api/delivery/**` → delivery-backend |
| `miniapp-notification` | Персональные уведомления (этап 2+) |

## API

- Внешний префикс: `/api/delivery/**`
- После gateway `StripPrefix=2`: `/health`, `/me`, …

## БД

Schema `delivery`, таблицы из `001-foundation.yaml`.

## Этап 2 (реализовано)

- Organizations, members, couriers, RBAC
- `GET /me`, `PATCH /me/active-organization`
- Связь ресторан ↔ `courier_service_id`
- Frontend: маршрутизация по memberships, выбор организации

## Этап 3 (реализовано)

- CRUD точек забора (`/restaurants/{id}/pickup-points`, `/pickup-points/{id}`)
- CRUD каналов службы (`/channels`)
- M:N привязка ресторан ↔ каналы (`PUT /restaurants/{id}/channels`)
- UI: каналы службы, рестораны + привязки, точки забора, просмотр каналов рестораном

## Следующие этапы

4. Orders, publish, accept  
