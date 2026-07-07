# 04 — Domain enums

Путь: `delivery-backend/src/main/java/ru/kzn/buzanov/delivery/domain/`

## Партнёрская программа

| Enum | Значения | UI-лейбл (frontend) |
|------|----------|---------------------|
| PartnerReferrerType | COURIER, RESTAURANT | Курьер / Объект (`partnerProgramAdminUi.js`) |
| PartnerReferralType | RESTAURANT, COURIER | invitee в правилах |
| PartnerParticipantType | — | тип участника счёта |
| PartnerCalculationType | PERCENT, FIXED | % / фикс |
| PartnerCalculationBase | DELIVERY_PRICE, COURIER_EARNING, ... | база расчёта |
| PartnerPayoutMethod | BANK_TRANSFER, ... | способ выплаты |
| PartnerPayoutStatus | — | статус заявки на выплату |
| PartnerAccrualStatus | — | статус начисления |
| PartnerReferralJournalStatus | INVITEE_INACTIVE, ... | статус реферала |

**Критично:** в UI «объект» всегда `RESTAURANT`, не `OBJECT`.

Матрица связок в frontend: `utils/partnerProgramAdminUi.js` → `RULE_PAIR_META`:
- `COURIER_COURIER`, `COURIER_RESTAURANT`, `RESTAURANT_COURIER`, `RESTAURANT_RESTAURANT`

## Заказы

| Enum | Файл |
|------|------|
| OrderStatus | `OrderStatus.java` |
| PublicationStatus | `PublicationStatus.java` |
| ChannelPostStatus | `ChannelPostStatus.java` |
| PriceSource | `PriceSource.java` |

## Организации и роли

| Enum | Значения |
|------|----------|
| OrganizationType | courier_service, ... |
| MemberRole | owner, admin, courier, ... |
| MemberStatus | active, ... |
| UserDeliveryStatus | — |
| DeliveryAccountStatus | — |

## Каналы

| Enum | Значения |
|------|----------|
| ChannelPlatform | TELEGRAM, MAX, ... |
| ChatType | — |

## Заявки

| Enum | Файл |
|------|------|
| CourierRequestStatus | `CourierRequestStatus.java` |
| RestaurantRegistrationRequestStatus | `RestaurantRegistrationRequestStatus.java` |
| RestaurantRegistrationSourceType | `RestaurantRegistrationSourceType.java` |

## Баланс

| Enum | Файл |
|------|------|
| BalanceTransactionType | `BalanceTransactionType.java` |

## Где смотреть лейблы на frontend

- `utils/displayLabels.js` — общие статусы
- `utils/partnerProgramAdminUi.js` — партнёрка
- `utils/channelFormLabels.js` — каналы
