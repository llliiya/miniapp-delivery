package ru.kzn.buzanov.delivery.service;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.kzn.buzanov.delivery.api.OrderConflictException;
import ru.kzn.buzanov.delivery.domain.DeliveryOrder;
import ru.kzn.buzanov.delivery.domain.OrderStatus;
import ru.kzn.buzanov.delivery.domain.Organization;
import ru.kzn.buzanov.delivery.domain.OrganizationMember;
import ru.kzn.buzanov.delivery.domain.PickupPoint;
import ru.kzn.buzanov.delivery.domain.PriceSource;
import ru.kzn.buzanov.delivery.dto.CreateOrderResponseDto;
import ru.kzn.buzanov.delivery.dto.OrderDto;
import ru.kzn.buzanov.delivery.dto.PatchOrderResponseDto;
import ru.kzn.buzanov.delivery.dto.RepublishOrderResponseDto;
import ru.kzn.buzanov.delivery.dto.request.ChangeOrderStatusRequest;
import ru.kzn.buzanov.delivery.dto.request.CreateOrderRequest;
import ru.kzn.buzanov.delivery.dto.request.PatchOrderRequest;
import ru.kzn.buzanov.delivery.repository.CourierProfileRepository;
import ru.kzn.buzanov.delivery.repository.DeliveryOrderRepository;
import ru.kzn.buzanov.delivery.repository.OrganizationMemberRepository;
import ru.kzn.buzanov.delivery.repository.OrganizationRepository;
import ru.kzn.buzanov.delivery.repository.PickupPointRepository;
import ru.kzn.buzanov.delivery.service.notification.CourierMessengerNotificationService;
import ru.kzn.buzanov.delivery.service.publication.OrderChannelProjectionService;
import ru.kzn.buzanov.delivery.service.publication.OrderPublicationService;
import ru.kzn.buzanov.delivery.service.realtime.OrderAssignmentEventPublisher;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final DeliveryOrderRepository orderRepository;
    private final CourierProfileRepository courierProfileRepository;
    private final OrganizationRepository organizationRepository;
    private final OrganizationMemberRepository memberRepository;
    private final PickupPointRepository pickupPointRepository;
    private final AccessControlService accessControl;
    private final OrderAccessService orderAccess;
    private final OrderStatusTransitionService statusTransition;
    private final OrderPublicationService publicationService;
    private final OrderChannelProjectionService channelProjectionService;
    private final CourierMessengerNotificationService courierMessengerNotificationService;
    private final OrderAssignmentEventPublisher assignmentEventPublisher;

    @Transactional
    public CreateOrderResponseDto create(Long userId, CreateOrderRequest request) {
        Organization restaurant = accessControl.requireRestaurant(request.restaurantId());
        orderAccess.requireCanCreateOrder(userId, restaurant);
        if (restaurant.getCourierServiceId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "У ресторана не указана курьерская служба");
        }
        PickupPoint pickup = requireRestaurantPickup(request.pickupPointId(), restaurant.getId());

        Instant now = Instant.now();
        DeliveryOrder order = new DeliveryOrder();
        order.setId(UUID.randomUUID());
        order.setCourierServiceId(restaurant.getCourierServiceId());
        order.setRestaurantId(restaurant.getId());
        applyPickup(order, pickup);
        order.setDeliveryAddress(request.deliveryAddress().trim());
        order.setDeliveryAddressFull(trimToNull(request.deliveryAddressFull()));
        order.setDeliveryLat(request.deliveryLat());
        order.setDeliveryLon(request.deliveryLon());
        order.setDeliveryApartment(trimToNull(request.apartment()));
        order.setDeliveryEntrance(trimToNull(request.entrance()));
        order.setDeliveryTime(request.deliveryTime());
        order.setPrice(request.price());
        order.setPriceSource(PriceSource.manual);
        order.setCustomerPhone(request.customerPhone().trim());
        order.setComment(trimToNull(request.comment()));
        order.setStatus(OrderStatus.waiting_for_courier);
        order.setCreatedByUserId(userId);
        order.setCreatedAt(now);
        order = orderRepository.saveAndFlush(order);
        order = orderRepository.findById(order.getId()).orElseThrow();

        List<String> warnings = new ArrayList<>(publicationService.publishNewOrder(order));
        order = orderRepository.save(order);
        return new CreateOrderResponseDto(toDto(order, userId), warnings);
    }

    @Transactional(readOnly = true)
    public List<OrderDto> list(
            Long userId,
            String scope,
            OrderStatus status,
            UUID restaurantId,
            UUID courierServiceId,
            Instant dateFrom,
            Instant dateTo) {
        Specification<DeliveryOrder> spec = buildListSpec(userId, scope, status, restaurantId, courierServiceId, dateFrom, dateTo);
        return orderRepository.findAll(spec).stream().map(order -> toDto(order, userId)).toList();
    }

    @Transactional(readOnly = true)
    public OrderDto get(Long userId, UUID orderId) {
        DeliveryOrder order = requireOrder(orderId);
        orderAccess.requireCanViewOrder(userId, order);
        return toDto(order, userId);
    }

    @Transactional
    public RepublishOrderResponseDto republish(Long userId, UUID orderId) {
        DeliveryOrder order = requireOrder(orderId);
        orderAccess.requireCanManageOrder(userId, order);
        orderAccess.requireRepublishable(order);
        List<String> warnings = new ArrayList<>(publicationService.republishOrder(order));
        order = orderRepository.save(order);
        return new RepublishOrderResponseDto(toDto(order, userId), warnings);
    }

    @Transactional
    public PatchOrderResponseDto patch(Long userId, UUID orderId, PatchOrderRequest request) {
        DeliveryOrder order = requireOrder(orderId);
        orderAccess.requireCourierCannotMutate(userId, order);
        orderAccess.requireCanManageOrder(userId, order);
        orderAccess.requireEditable(order);

        boolean changed = false;
        if (request.pickupPointId() != null) {
            PickupPoint pickup = requireRestaurantPickup(request.pickupPointId(), order.getRestaurantId());
            applyPickup(order, pickup);
            changed = true;
        }
        if (request.deliveryAddress() != null && !request.deliveryAddress().isBlank()) {
            order.setDeliveryAddress(request.deliveryAddress().trim());
            changed = true;
        }
        if (request.deliveryAddressFull() != null) {
            order.setDeliveryAddressFull(trimToNull(request.deliveryAddressFull()));
            changed = true;
        }
        if (request.apartment() != null) {
            order.setDeliveryApartment(trimToNull(request.apartment()));
            changed = true;
        }
        if (request.entrance() != null) {
            order.setDeliveryEntrance(trimToNull(request.entrance()));
            changed = true;
        }
        if (request.deliveryLat() != null) {
            order.setDeliveryLat(request.deliveryLat());
            changed = true;
        }
        if (request.deliveryLon() != null) {
            order.setDeliveryLon(request.deliveryLon());
            changed = true;
        }
        if (request.deliveryTime() != null) {
            order.setDeliveryTime(request.deliveryTime());
            changed = true;
        }
        if (request.price() != null) {
            if (request.price().signum() <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Стоимость должна быть положительной");
            }
            order.setPrice(request.price());
            changed = true;
        }
        if (request.customerPhone() != null && !request.customerPhone().isBlank()) {
            order.setCustomerPhone(request.customerPhone().trim());
            changed = true;
        }
        if (request.comment() != null) {
            order.setComment(trimToNull(request.comment()));
            changed = true;
        }
        order = orderRepository.save(order);
        if (changed && order.getPublishedAt() != null) {
            channelProjectionService.syncOrder(order, resolveCourierDisplayName(order, order.getCourierUserId()));
        }
        List<String> warnings = List.of();
        return new PatchOrderResponseDto(toDto(order, userId), warnings);
    }

    @Transactional
    public OrderDto cancel(Long userId, UUID orderId) {
        DeliveryOrder order = requireOrder(orderId);
        orderAccess.requireCourierCannotMutate(userId, order);
        orderAccess.requireCanManageOrder(userId, order);
        orderAccess.requireCancellable(order);
        order.setStatus(OrderStatus.cancelled);
        order.setCancelledAt(Instant.now());
        order = orderRepository.save(order);
        if (order.getPublishedAt() != null) {
            channelProjectionService.syncOrder(order, resolveCourierDisplayName(order, order.getCourierUserId()));
        }
        return toDto(order, userId);
    }

    @Transactional
    public OrderDto assign(Long actorUserId, UUID orderId, Long courierId) {
        if (!actorUserId.equals(courierId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Нельзя назначить заказ от имени другого курьера");
        }
        DeliveryOrder order = requireOrder(orderId);
        orderAccess.requireActiveCourierForAccept(courierId, order);
        Instant now = Instant.now();
        int updated = orderRepository.assignOrderIfUnassigned(orderId, courierId, now);
        if (updated == 0) {
            DeliveryOrder fresh = requireOrder(orderId);
            if (fresh.getCourierUserId() != null) {
                throw new OrderConflictException("order_already_taken");
            }
            throw new OrderConflictException("order_not_available");
        }
        order = requireOrder(orderId);
        String courierName = resolveCourierDisplayName(order, courierId);
        if (order.getPublishedAt() != null) {
            channelProjectionService.syncOrder(order, courierName);
        }
        courierMessengerNotificationService.notifyOrderAssigned(order, courierId);
        assignmentEventPublisher.publishAssigned(order);
        return toDto(order, actorUserId);
    }

    @Transactional
    public OrderDto accept(Long userId, UUID orderId) {
        return assign(userId, orderId, userId);
    }

    @Transactional
    public OrderDto changeStatus(Long userId, UUID orderId, ChangeOrderStatusRequest request) {
        DeliveryOrder order = requireOrder(orderId);
        OrderStatus newStatus = request.status();
        OrderStatus currentStatus = order.getStatus();
        if (currentStatus == newStatus) {
            return toDto(order, userId);
        }

        boolean isCourier = orderAccess.isCourierOnlyForOrder(userId, order)
                || (order.getCourierUserId() != null && order.getCourierUserId().equals(userId)
                && orderAccess.findActiveCourierMembership(userId, order.getCourierServiceId()).isPresent());
        boolean isService = orderAccess.canManageServiceOrder(userId, order);
        boolean isRestaurantManager = orderAccess.isRestaurantManagerForOrder(userId, order);

        if (isCourier && !isService && !isRestaurantManager) {
            orderAccess.requireCourierOwnsOrder(userId, order);
            statusTransition.requireCourierCanTransition(currentStatus, newStatus);
        } else if (isService) {
            statusTransition.requireAllowedTransition(currentStatus, newStatus);
        } else if (isRestaurantManager) {
            statusTransition.requireRestaurantCanTransition(currentStatus, newStatus);
        } else {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Недостаточно прав");
        }

        applyStatusChange(order, newStatus);
        order = orderRepository.save(order);
        if (order.getPublishedAt() != null) {
            channelProjectionService.syncOrder(order, resolveCourierDisplayName(order, order.getCourierUserId()));
        }
        return toDto(order, userId);
    }

    private void applyStatusChange(DeliveryOrder order, OrderStatus newStatus) {
        Instant now = Instant.now();
        order.setStatus(newStatus);
        if (newStatus == OrderStatus.completed) {
            order.setCompletedAt(now);
            incrementCompletedOrdersCount(order);
        } else if (newStatus == OrderStatus.cancelled) {
            order.setCancelledAt(now);
        }
    }

    private void incrementCompletedOrdersCount(DeliveryOrder order) {
        if (order.getCourierUserId() == null) {
            return;
        }
        orderAccess.findActiveCourierMembership(order.getCourierUserId(), order.getCourierServiceId())
                .flatMap(member -> courierProfileRepository.findByMemberId(member.getId()))
                .ifPresent(profile -> {
                    profile.setCompletedOrdersCount(profile.getCompletedOrdersCount() + 1);
                    profile.setUpdatedAt(Instant.now());
                    courierProfileRepository.save(profile);
                });
    }

    private Specification<DeliveryOrder> buildListSpec(
            Long userId,
            String scope,
            OrderStatus status,
            UUID restaurantId,
            UUID courierServiceId,
            Instant dateFrom,
            Instant dateTo) {
        String normalizedScope = scope == null ? "" : scope.trim().toLowerCase();
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            switch (normalizedScope) {
                case "courier", "courier_free", "free" -> {
                    UUID serviceId = orderAccess.findCourierServiceIdForUser(userId)
                            .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Нет курьерского доступа"));
                    predicates.add(cb.equal(root.get("courierServiceId"), serviceId));
                    if (status == null || status == OrderStatus.waiting_for_courier) {
                        predicates.add(cb.equal(root.get("status"), OrderStatus.waiting_for_courier));
                        predicates.add(cb.isNull(root.get("courierUserId")));
                    } else {
                        predicates.add(cb.equal(root.get("courierUserId"), userId));
                        predicates.add(cb.equal(root.get("status"), status));
                    }
                }
                case "restaurant" -> {
                    if (restaurantId == null) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "restaurantId обязателен");
                    }
                    Organization restaurant = accessControl.requireRestaurant(restaurantId);
                    accessControl.requireCanViewOrganization(userId, restaurant);
                    predicates.add(cb.equal(root.get("restaurantId"), restaurantId));
                }
                case "service" -> {
                    if (courierServiceId == null) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "courierServiceId обязателен");
                    }
                    accessControl.requireServiceStaff(userId, courierServiceId);
                    predicates.add(cb.equal(root.get("courierServiceId"), courierServiceId));
                    if (restaurantId != null) {
                        predicates.add(cb.equal(root.get("restaurantId"), restaurantId));
                    }
                }
                default -> {
                    if (restaurantId != null) {
                        Organization restaurant = accessControl.requireRestaurant(restaurantId);
                        if (!accessControl.canViewOrganization(userId, restaurant)) {
                            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Нет доступа");
                        }
                        predicates.add(cb.equal(root.get("restaurantId"), restaurantId));
                    } else if (courierServiceId != null) {
                        try {
                            accessControl.requireServiceStaff(userId, courierServiceId);
                            predicates.add(cb.equal(root.get("courierServiceId"), courierServiceId));
                        } catch (ResponseStatusException e) {
                            orderAccess.findCourierServiceIdForUser(userId).ifPresentOrElse(
                                    sid -> {
                                        if (!sid.equals(courierServiceId)) {
                                            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Нет доступа");
                                        }
                                        predicates.add(cb.equal(root.get("courierServiceId"), sid));
                                        predicates.add(cb.equal(root.get("status"), OrderStatus.waiting_for_courier));
                                        predicates.add(cb.isNull(root.get("courierUserId")));
                                    },
                                    () -> {
                                        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Нет доступа");
                                    });
                        }
                    } else {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Укажите scope или restaurantId/courierServiceId");
                    }
                }
            }

            if (status != null && !normalizedScope.equals("courier")
                    && !normalizedScope.equals("courier_free") && !normalizedScope.equals("free")) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (dateFrom != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), dateFrom));
            }
            if (dateTo != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), dateTo));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private PickupPoint requireRestaurantPickup(UUID pickupPointId, UUID restaurantId) {
        PickupPoint pickup = pickupPointRepository.findById(pickupPointId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Точка забора не найдена"));
        if (!pickup.getRestaurantId().equals(restaurantId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Точка забора не принадлежит ресторану");
        }
        return pickup;
    }

    private void applyPickup(DeliveryOrder order, PickupPoint pickup) {
        order.setPickupPointId(pickup.getId());
        order.setPickupAddress(pickup.getAddress());
        order.setPickupLat(pickup.getLat());
        order.setPickupLon(pickup.getLon());
    }

    private DeliveryOrder requireOrder(UUID orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Заказ не найден"));
    }

    private OrderDto toDto(DeliveryOrder order, Long userId) {
        OrderDto dto = buildDto(order);
        if (shouldMaskPrivateCustomerData(userId, order)) {
            return maskPrivateCustomerData(dto);
        }
        return dto;
    }

    private OrderDto buildDto(DeliveryOrder order) {
        String restaurantName = organizationRepository.findById(order.getRestaurantId())
                .map(Organization::getName)
                .orElse(null);
        Long courierPublicId = null;
        String courierDisplayName = null;
        if (order.getCourierUserId() != null && order.getCourierServiceId() != null) {
            Optional<OrganizationMember> courierMember = memberRepository.findByOrganizationIdAndUserId(
                    order.getCourierServiceId(), order.getCourierUserId());
            if (courierMember.isPresent()) {
                courierPublicId = courierMember.get().getPublicId();
                courierDisplayName = courierMember.get().getDisplayName();
            }
        }
        return new OrderDto(
                order.getId(),
                order.getPublicNumber(),
                order.getCourierServiceId(),
                order.getRestaurantId(),
                order.getPickupPointId(),
                order.getPickupAddress(),
                order.getDeliveryAddress(),
                order.getDeliveryAddressFull(),
                order.getDeliveryApartment(),
                order.getDeliveryEntrance(),
                order.getPickupLat(),
                order.getPickupLon(),
                order.getDeliveryLat(),
                order.getDeliveryLon(),
                order.getDeliveryTime(),
                order.getPrice(),
                order.getPriceSource(),
                order.getCustomerPhone(),
                order.getComment(),
                order.getStatus(),
                order.getCourierUserId(),
                courierPublicId,
                courierDisplayName,
                restaurantName,
                order.getCreatedByUserId(),
                order.getCreatedAt(),
                order.getPublishedAt(),
                order.getAcceptedAt(),
                order.getCompletedAt(),
                order.getCancelledAt(),
                publicationService.publicationFailures(order),
                publicationService.canRepublish(order)
        );
    }

    private boolean shouldMaskPrivateCustomerData(Long userId, DeliveryOrder order) {
        if (order.getCourierUserId() != null && userId.equals(order.getCourierUserId())) {
            return false;
        }
        if (orderAccess.isRestaurantStaffForOrder(userId, order) || orderAccess.canManageServiceOrder(userId, order)) {
            return false;
        }
        return order.getStatus() == OrderStatus.waiting_for_courier
                && order.getCourierUserId() == null
                && orderAccess.findActiveCourierMembership(userId, order.getCourierServiceId()).isPresent();
    }

    private String resolveCourierDisplayName(DeliveryOrder order, Long courierUserId) {
        if (courierUserId == null || order.getCourierServiceId() == null) {
            return "Курьер";
        }
        return memberRepository.findByOrganizationIdAndUserId(order.getCourierServiceId(), courierUserId)
                .map(OrganizationMember::getDisplayName)
                .filter(name -> name != null && !name.isBlank())
                .orElse("Курьер");
    }

    private static OrderDto maskPrivateCustomerData(OrderDto dto) {
        return new OrderDto(
                dto.id(),
                dto.publicNumber(),
                dto.courierServiceId(),
                dto.restaurantId(),
                dto.pickupPointId(),
                dto.pickupAddress(),
                dto.deliveryAddress(),
                null,
                null,
                null,
                dto.pickupLat(),
                dto.pickupLon(),
                null,
                null,
                dto.deliveryTime(),
                dto.price(),
                dto.priceSource(),
                null,
                null,
                dto.status(),
                dto.courierUserId(),
                dto.courierPublicId(),
                dto.courierDisplayName(),
                dto.restaurantName(),
                dto.createdByUserId(),
                dto.createdAt(),
                dto.publishedAt(),
                dto.acceptedAt(),
                dto.completedAt(),
                dto.cancelledAt(),
                dto.publicationFailures(),
                dto.canRepublish()
        );
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String t = value.trim();
        return t.isEmpty() ? null : t;
    }
}
