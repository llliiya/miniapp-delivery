# 03 — Партнёрская программа

## Важно: «объект» = RESTAURANT

В UI «объект → объект» = `referrerType: RESTAURANT`, `inviteeType: RESTAURANT`.
Отдельного enum `OBJECT` нет.

## Backend — цепочка файлов

| Слой | Файл |
|------|------|
| API | `api/PartnerProgramController.java` |
| Правила (CRUD/upsert) | `service/PartnerProgramRuleService.java` |
| ЛК участника | `service/PartnerProgramService.java` |
| Выплаты | `service/PartnerPayoutService.java` |
| Рефералы (админ) | `service/PartnerReferralAdminService.java` |
| Начисления | `service/PartnerAccrualService.java`, `PartnerAccrualCalculator.java` |
| Счета | `service/PartnerAccountService.java` |
| Реферальные связи | `service/PartnerReferralService.java` |
| Request DTO | `dto/request/UpsertPartnerProgramRuleRequest.java`, `CreatePartnerPayoutRequest.java` |
| Response DTO | `dto/PartnerProgramDto.java`, `PartnerProgramRuleDto.java`, `PartnerConnectedReferralDto.java`, `PartnerBalanceSummaryDto.java`, `PartnerReferralAdminDto.java`, `PartnerPayoutRequestAdminDto.java` |
| Repository | `repository/PartnerProgramRuleRepository.java`, `PartnerReferralRepository.java`, `PartnerAccrualRepository.java`, `PartnerPayoutRequestRepository.java`, `PartnerAccountRepository.java` |
| JSON mapping | `util/PartnerJsonMapper.java` |
| Ошибки | `api/ApiExceptionHandler.java`, `api/PartnerPayoutConflictException.java` |

## Frontend — цепочка файлов

| Экран | Файл |
|-------|------|
| ЛК курьера | `pages/courier/CourierProfilePage.jsx` → `#partner-program` |
| ЛК объекта | `pages/restaurant/RestaurantProfilePage.jsx` → `#partner-program` |
| UI блок | `components/partner/PartnerProgramSection.jsx` |
| Админ правил | `pages/service/ServicePartnerProgramRulesPage.jsx` |
| Карточка правила | `components/partner/PartnerProgramRuleCard.jsx` |
| Вкладка выплат | `pages/service/ServicePartnerProgramPayoutsTab.jsx` |
| Вкладка рефералов | `pages/service/ServicePartnerProgramReferralsTab.jsx` |
| Лейблы/матрица | `utils/partnerProgramAdminUi.js` |
| API | `api/deliveryService.js` → `fetch*Partner*`, `upsertPartnerProgramRule` |

## API

```
GET  /partner-program/rules?courierServiceId={uuid}
PUT  /partner-program/rules?courierServiceId={uuid}   ← upsert одного правила
GET  /couriers/{memberId}/partner-program
GET  /restaurants/{restaurantId}/partner-program
POST /couriers/{memberId}/partner-program/payout-requests
POST /restaurants/{restaurantId}/partner-program/payout-requests
GET  /partner-program/payout-requests?courierServiceId=
GET  /partner-program/referrals?courierServiceId=
POST /partner-program/payout-requests/{id}/process?courierServiceId=&approve=
```

## UpsertPartnerProgramRuleRequest — ключевые поля

```
referrerType:     COURIER | RESTAURANT
inviteeType:      COURIER | RESTAURANT  (в request — inviteeType)
enabled:          boolean
calculationType:  PERCENT | FIXED
percentValue / fixedAmount
calculationBase:  DELIVERY_PRICE | COURIER_EARNING | ...
durationMonths
effectiveFrom:    ISO date
accrualConditions: { onlyCompleted: boolean }
payoutRestrictions: { payoutDayOfMonth: 1-28 }
minPayoutAmount
payoutMethods:    [BANK_TRANSFER, ...]
```

## Enum'ы (см. также 04-domain-enums.md)

- `PartnerReferrerType`: COURIER, RESTAURANT
- `PartnerReferralType`: RESTAURANT, COURIER (invitee)
- `PartnerCalculationType`: PERCENT, FIXED
- `PartnerCalculationBase`: DELIVERY_PRICE, COURIER_EARNING, ...
- `PartnerPayoutMethod`: BANK_TRANSFER, ...
- `PartnerPayoutStatus`, `PartnerAccrualStatus`, `PartnerReferralJournalStatus`

## Миграции БД

- `db/changelog/changes/016-restaurant-registration-partner.yaml`
- `db/changelog/changes/017-backfill-courier-partner-codes.yaml`
- `db/changelog/changes/018-partner-program-expansion.yaml`
- `db/changelog/changes/020-partner-program-financial.yaml`

## Тесты

```
PartnerProgramRuleServiceUpsertTest      — upsert правил, OBJECT→OBJECT = RESTAURANT→RESTAURANT
PartnerProgramRuleServiceVisibilityTest  — видимость правил
PartnerPayoutServiceTest
PartnerReferralAdminServiceTest
PartnerAccrualCalculatorTest
ApiExceptionHandlerTest
```

Запуск:
```powershell
cd c:\valentin\miniapp-delivery\delivery-backend
mvn -Dtest=PartnerProgramRuleServiceUpsertTest test
```

## Типичные причины 500 при сохранении правила

1. Несовпадение enum frontend/backend (OBJECT vs RESTAURANT)
2. Liquibase миграция не применена → duplicate key / missing column
3. Невалидный `payoutDayOfMonth`, `calculationBase`, `payoutMethods`
4. Unique constraint на пару (courierServiceId, referrerType, inviteeType)

## Проверка после фикса

```powershell
curl.exe -s -w "\nHTTP:%{http_code}\n" http://localhost:8080/api/delivery/health
docker logs delivery_dev-delivery-backend-1 --tail 50
```

PUT требует auth — без токена ожидай 401, не 500.
