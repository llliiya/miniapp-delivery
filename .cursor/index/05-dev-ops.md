# 05 — Dev, Docker, тесты, логи

## Запуск стека

Из `c:\valentin`:
```powershell
docker compose -f miniapp-delivery/docker-compose.dev.yml up --build -d
```

## Сервисы compose

| Service | Порт | Контейнер (пример) |
|---------|------|---------------------|
| db | 5430→5432 | delivery_dev-db-1 |
| gateway | 8080 | delivery_dev-gateway-1 |
| delivery-backend | 9081 (internal) | delivery_dev-delivery-backend-1 |
| delivery-frontend | 5173 | delivery_dev-delivery-frontend-1 |
| account | 8092 | delivery_dev-account-1 |
| notification | 8093 | delivery_dev-notification-1 |

## Пересборка после правок

```powershell
cd C:\valentin\miniapp-delivery

# Backend — только по явной команде, скрипт пересобирает один сервис
powershell -ExecutionPolicy Bypass -File .\scripts\rebuild-dev-backend.ps1

# Логи backend
powershell -ExecutionPolicy Bypass -File .\scripts\logs-dev-backend.ps1

# Frontend (с --no-cache!)
docker compose -f .\docker-compose.dev.yml build --no-cache delivery-frontend
docker compose -f .\docker-compose.dev.yml up -d --no-deps delivery-frontend
```

Скрипт: `scripts/rebuild-dev-backend.ps1`

## Health check

```powershell
curl.exe -s -w "\nHTTP:%{http_code}\n" http://localhost:8080/api/delivery/health
```

## Логи

```powershell
docker logs delivery_dev-delivery-backend-1 --tail 80
docker logs delivery_dev-gateway-1 --tail 30
docker ps -a --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
```

## Maven-тесты (без Docker)

```powershell
cd c:\valentin\miniapp-delivery\delivery-backend
mvn -Dtest=ClassName test
mvn test   # все тесты — только по явной просьбе
```

## Liquibase

- Master: `src/main/resources/db/changelog/db.changelog-master.yaml`
- Changes: `src/main/resources/db/changelog/changes/NNN-*.yaml`
- Schema: `delivery`, liquibase metadata в `public`

Если backend не стартует с ошибкой схемы — проверь, применились ли последние changesets.

## Postgres напрямую

```
Host: localhost:5430
DB: delivery
User/Pass: postgres/postgres
Schema: delivery
```

## Профили Spring

- `application.yml` — база
- `application-dev.yml` — dev overrides
- Profile: `SPRING_PROFILES_ACTIVE=dev`

## Что НЕ делать без просьбы

- `docker compose build` всего стека
- `mvn clean install` полный
- Удаление volumes Postgres
