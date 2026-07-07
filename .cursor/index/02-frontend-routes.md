# 02 — Frontend routes & pages

Точка входа: `delivery-frontend/src/App.jsx`

## Роутинг по ролям

### Service (`/service/*`) — `routes/ServiceRoutes.jsx`

| Path | Page |
|------|------|
| `/service/orders` | `pages/service/ServiceOrdersPage.jsx` |
| `/service/orders/new` | `pages/shared/NewOrderPage.jsx` |
| `/service/orders/:orderId` | `pages/service/ServiceOrderDetailPage.jsx` |
| `/service/restaurants` | `pages/service/ObjectsPage.jsx` |
| `/service/restaurants/new` | `pages/service/AddObjectPage.jsx` |
| `/service/restaurants/:id` | `pages/service/ObjectDetailPage.jsx` |
| `/service/restaurants/:id/staff` | `pages/service/ObjectStaffPage.jsx` |
| `/service/restaurants/:id/channels` | `pages/service/ObjectChannelsPage.jsx` |
| `/service/restaurants/:id/pickup` | `pages/restaurant/PickupPointsPage.jsx` |
| `/service/couriers` | `pages/service/ServiceCouriersPage.jsx` |
| `/service/couriers/:id` | `pages/service/ServiceCourierDetailPage.jsx` |
| `/service/channels` | `pages/service/ChannelsPage.jsx` |
| `/service/profile` | `pages/service/ServiceProfilePage.jsx` |
| `/service/partner-program` | `pages/service/ServicePartnerProgramRulesPage.jsx` |

### Courier (`/courier/*`) — `routes/CourierRoutes.jsx`

| Path | Page |
|------|------|
| `/courier/orders` | `pages/courier/CourierOrdersPage.jsx` |
| `/courier/orders/:orderId` | `pages/courier/CourierOrderDetailPage.jsx` |
| `/courier/my-orders` | `pages/courier/CourierMyOrdersPage.jsx` |
| `/courier/my-orders/:orderId` | `pages/courier/CourierMyOrderDetailPage.jsx` |
| `/courier/map` | `pages/courier/CourierMapPage.jsx` |
| `/courier/profile` | `pages/courier/CourierProfilePage.jsx` |

### Restaurant / Object (`/restaurant/*`) — `routes/RestaurantRoutes.jsx`

| Path | Page |
|------|------|
| `/restaurant/orders` | `pages/restaurant/RestaurantOrdersPage.jsx` |
| `/restaurant/orders/new` | `pages/shared/NewOrderPage.jsx` |
| `/restaurant/orders/:orderId` | `pages/restaurant/RestaurantOrderDetailPage.jsx` |
| `/restaurant/pickup` | `pages/restaurant/PickupPointsPage.jsx` |
| `/restaurant/channels` | `pages/restaurant/RestaurantChannelsPage.jsx` |
| `/restaurant/staff` | `pages/restaurant/RestaurantStaffPage.jsx` |
| `/restaurant/profile` | `pages/restaurant/RestaurantProfilePage.jsx` |

## Ключевые компоненты по фичам

| Фича | Компоненты |
|------|------------|
| Партнёрка (ЛК объекта/курьера) | `components/partner/PartnerProgramSection.jsx` |
| Партнёрка (админ службы) | `pages/service/ServicePartnerProgramRulesPage.jsx`, `PartnerProgramRuleCard.jsx`, `ServicePartnerProgramPayoutsTab.jsx`, `ServicePartnerProgramReferralsTab.jsx` |
| Заказы | `components/orders/*` |
| Объекты | `components/objects/*` |
| Каналы | `components/channels/*` |
| Auth | `pages/auth/*`, `context/AuthContext.jsx` |

## Утилиты UI

| Файл | Назначение |
|------|------------|
| `utils/displayLabels.js` | Общие лейблы статусов |
| `utils/partnerProgramAdminUi.js` | Лейблы партнёрки, матрица правил, бейджи |
| `utils/mapApiError.js` | Ошибки API → текст для пользователя |
| `utils/deeplink.js` | Deeplink из Telegram/MAX |
| `hooks/useActiveOrg.js` | Активная курьерская служба |

## Стили партнёрки

Классы `partner-program-*` в `delivery-frontend/src/index.css`

## API-слой

`api/deliveryService.js` — единственное место HTTP-вызовов к backend.
`api/http.js` — обёртка fetch, токен, base URL из `config.js`.
