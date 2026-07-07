# 01 — API endpoints

Префикс: `/api/delivery` (через gateway :8080).

Контроллеры: `delivery-backend/src/main/java/ru/kzn/buzanov/delivery/api/`

## Me

| Method | Path | Controller |
|--------|------|------------|
| GET | `/me` | MeController |
| PATCH | `/me/active-organization` | MeController |

## Organizations (курьерские службы)

| Method | Path | Controller |
|--------|------|------------|
| POST | `/organizations` | OrganizationController |
| GET | `/organizations` | OrganizationController |
| GET | `/organizations/{id}` | OrganizationController |
| PATCH | `/organizations/{id}` | OrganizationController |
| GET | `/organizations/{id}/cities` | OrganizationController |
| GET | `/organizations/{id}/members` | OrganizationController |
| POST | `/organizations/{id}/members` | OrganizationController |
| PATCH | `/organizations/{id}/members/{userId}` | OrganizationController |
| POST | `/organizations/{id}/members/{userId}/reset-access` | OrganizationController |
| DELETE | `/organizations/{id}/members/{userId}` | OrganizationController |

## Restaurants (объекты)

| Method | Path | Controller |
|--------|------|------------|
| POST | `/restaurants` | RestaurantController |
| GET | `/restaurants` | RestaurantController |
| GET | `/restaurants/{id}` | RestaurantController |
| PATCH | `/restaurants/{id}` | RestaurantController |

## Couriers

| Method | Path | Controller |
|--------|------|------------|
| GET | `/couriers` | CourierController |
| POST | `/couriers` | CourierController |
| GET | `/couriers/{memberId}` | CourierController |
| PATCH | `/couriers/{memberId}` | CourierController |
| POST | `/couriers/{memberId}/reset-access` | CourierController |

## Orders

| Method | Path | Controller |
|--------|------|------------|
| POST | `/orders` | OrderController |
| GET | `/orders` | OrderController |
| GET | `/orders/{id}` | OrderController |
| PATCH | `/orders/{id}` | OrderController |
| POST | `/orders/{id}/cancel` | OrderController |
| POST | `/orders/{id}/republish` | OrderController |
| POST | `/orders/{id}/assign` | OrderController |
| POST | `/orders/{id}/accept` | OrderController |
| POST | `/orders/{id}/status` | OrderController |
| GET | `/orders/events` (SSE) | OrderEventsController |

## Pickup points

| Method | Path | Controller |
|--------|------|------------|
| GET | `/restaurants/{restaurantId}/pickup-points` | PickupPointController |
| POST | `/restaurants/{restaurantId}/pickup-points` | PickupPointController |
| PATCH | `/pickup-points/{id}` | PickupPointController |
| DELETE | `/pickup-points/{id}` | PickupPointController |

## Channels

| Method | Path | Controller |
|--------|------|------------|
| GET | `/channels` | PublicationChannelController |
| POST | `/channels` | PublicationChannelController |
| PATCH | `/channels/{id}` | PublicationChannelController |
| DELETE | `/channels/{id}` | PublicationChannelController |
| GET | `/restaurants/{restaurantId}/channels` | RestaurantChannelController |
| PUT | `/restaurants/{restaurantId}/channels` | RestaurantChannelController |

## Partner program

| Method | Path | Controller | Service |
|--------|------|------------|---------|
| GET | `/couriers/{memberId}/partner-program` | PartnerProgramController | PartnerProgramService |
| GET | `/restaurants/{restaurantId}/partner-program` | PartnerProgramController | PartnerProgramService |
| POST | `/couriers/{memberId}/partner-program/payout-requests` | PartnerProgramController | PartnerPayoutService |
| POST | `/restaurants/{restaurantId}/partner-program/payout-requests` | PartnerProgramController | PartnerPayoutService |
| GET | `/partner-program/rules?courierServiceId=` | PartnerProgramController | PartnerProgramRuleService |
| PUT | `/partner-program/rules?courierServiceId=` | PartnerProgramController | PartnerProgramRuleService |
| GET | `/partner-program/payout-requests?courierServiceId=` | PartnerProgramController | PartnerPayoutService |
| GET | `/partner-program/referrals?courierServiceId=` | PartnerProgramController | PartnerReferralAdminService |
| POST | `/partner-program/payout-requests/{id}/process` | PartnerProgramController | PartnerPayoutService |

Подробнее: `03-feature-partner-program.md`

## Registration / requests

| Method | Path | Controller |
|--------|------|------------|
| POST | `/public/courier-requests` | CourierRequestController |
| GET | `/public/courier-requests/messenger-status` | CourierRequestController |
| GET | `/courier-requests` | CourierRequestController |
| PATCH | `/courier-requests/{id}/approve` | CourierRequestController |
| PATCH | `/courier-requests/{id}/reject` | CourierRequestController |
| POST | `/public/restaurant-registration-requests` | RestaurantRegistrationRequestController |
| GET | `/restaurant-registration-requests` | RestaurantRegistrationRequestController |
| GET | `/restaurant-registration-requests/{id}` | RestaurantRegistrationRequestController |
| PATCH | `/restaurant-registration-requests/{id}/in-progress` | RestaurantRegistrationRequestController |
| PATCH | `/restaurant-registration-requests/{id}/approve` | RestaurantRegistrationRequestController |
| PATCH | `/restaurant-registration-requests/{id}/reject` | RestaurantRegistrationRequestController |

## Webhooks / health

| Method | Path | Controller |
|--------|------|------------|
| GET | `/health` | HealthController |
| POST | `/webhook` (telegram) | DeliveryTelegramWebhookController |
| POST | `/webhook` (max) | DeliveryMaxWebhookController |

## Frontend → API mapping

Все функции в `delivery-frontend/src/api/deliveryService.js` — имя функции ≈ endpoint.
