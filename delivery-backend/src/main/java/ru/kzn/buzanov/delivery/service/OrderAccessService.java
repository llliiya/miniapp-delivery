package ru.kzn.buzanov.delivery.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import ru.kzn.buzanov.delivery.domain.DeliveryOrder;
import ru.kzn.buzanov.delivery.domain.MemberRole;
import ru.kzn.buzanov.delivery.domain.MemberStatus;
import ru.kzn.buzanov.delivery.domain.OrderStatus;
import ru.kzn.buzanov.delivery.domain.Organization;
import ru.kzn.buzanov.delivery.domain.OrganizationMember;
import ru.kzn.buzanov.delivery.domain.OrganizationType;
import ru.kzn.buzanov.delivery.dto.OrderEventSubscription;
import ru.kzn.buzanov.delivery.repository.OrganizationMemberRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderAccessService {

    private final AccessControlService accessControl;
    private final OrganizationMemberRepository memberRepository;
    private final DeliveryUserProfileService profileService;

    public void requireCanCreateOrder(Long userId, Organization restaurant) {
        accessControl.requireRestaurant(restaurant.getId());
        if (!restaurant.isActive()) {
            throw new ResponseStatusException(HttpStatus.GONE, "Объект деактивирован. Новые заказы создавать нельзя.");
        }
        if (accessControl.isRestaurantManager(userId, restaurant)) {
            return;
        }
        if (restaurant.getCourierServiceId() != null
                && accessControl.isServiceStaffForCourierService(userId, restaurant.getCourierServiceId())) {
            return;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Недостаточно прав для создания заказа");
    }

    public UUID resolveCreatorOrganizationId(Long userId, Organization restaurant) {
        if (accessControl.isRestaurantManager(userId, restaurant)) {
            return restaurant.getId();
        }
        if (restaurant.getCourierServiceId() != null
                && accessControl.isServiceStaffForCourierService(userId, restaurant.getCourierServiceId())) {
            return restaurant.getCourierServiceId();
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Недостаточно прав для создания заказа");
    }

    public void requireCanViewOrder(Long userId, DeliveryOrder order) {
        if (!canViewOrder(userId, order)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Нет доступа к заказу");
        }
    }

    public void requireCanManageOrder(Long userId, DeliveryOrder order) {
        if (!canManageOrder(userId, order)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Недостаточно прав");
        }
    }

    public void requireCourierCannotMutate(Long userId, DeliveryOrder order) {
        if (isCourierOnlyForOrder(userId, order)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Курьер не может изменять заказ");
        }
    }

    public boolean canViewOrder(Long userId, DeliveryOrder order) {
        Organization restaurant = accessControl.requireRestaurant(order.getRestaurantId());
        if (accessControl.canManageRestaurantResource(userId, restaurant)
                || accessControl.canViewOrganization(userId, restaurant)) {
            return true;
        }
        if (canManageServiceOrder(userId, order)) {
            return true;
        }
        return canCourierViewOrder(userId, order);
    }

    public boolean canManageOrder(Long userId, DeliveryOrder order) {
        Organization restaurant = accessControl.requireRestaurant(order.getRestaurantId());
        if (accessControl.canManageRestaurantResource(userId, restaurant)) {
            return true;
        }
        return canManageServiceOrder(userId, order);
    }

    public boolean canCourierViewOrder(Long userId, DeliveryOrder order) {
        Optional<OrganizationMember> courierMember = findActiveCourierMembership(userId, order.getCourierServiceId());
        if (courierMember.isEmpty()) {
            return false;
        }
        if (order.getCourierUserId() != null && order.getCourierUserId().equals(userId)) {
            return true;
        }
        return order.getStatus() == OrderStatus.waiting_for_courier && order.getCourierUserId() == null;
    }

    public boolean canManageServiceOrder(Long userId, DeliveryOrder order) {
        try {
            accessControl.requireServiceStaff(userId, order.getCourierServiceId());
            return true;
        } catch (ResponseStatusException e) {
            return false;
        }
    }

    public boolean isCourierOnlyForOrder(Long userId, DeliveryOrder order) {
        if (canManageOrder(userId, order)) {
            return false;
        }
        return findActiveCourierMembership(userId, order.getCourierServiceId()).isPresent();
    }

    public Optional<UUID> findCourierServiceIdForUser(Long userId) {
        List<OrganizationMember> members = memberRepository.findByUserIdAndStatus(userId, MemberStatus.active);
        for (OrganizationMember member : members) {
            if (member.getRole() != MemberRole.courier) {
                continue;
            }
            Organization org = accessControl.requireOrganization(member.getOrganizationId());
            if (org.getType() == OrganizationType.courier_service) {
                return Optional.of(org.getId());
            }
        }
        return Optional.empty();
    }

    public Optional<OrderEventSubscription> resolveOrderEventSubscription(Long userId) {
        Optional<UUID> courierServiceId = findCourierServiceIdForUser(userId);
        if (courierServiceId.isPresent()) {
            return Optional.of(new OrderEventSubscription(courierServiceId.get(), null));
        }
        UUID serviceId = findServiceStaffCourierServiceIdForUser(userId).orElse(null);
        UUID restaurantId = findRestaurantIdForUser(userId).orElse(null);
        if (serviceId == null && restaurantId == null) {
            return Optional.empty();
        }
        return Optional.of(new OrderEventSubscription(serviceId, restaurantId));
    }

    private Optional<UUID> findServiceStaffCourierServiceIdForUser(Long userId) {
        List<OrganizationMember> members = memberRepository.findByUserIdAndStatus(userId, MemberStatus.active);
        for (OrganizationMember member : members) {
            if (member.getRole() == MemberRole.courier) {
                continue;
            }
            Organization org = accessControl.requireOrganization(member.getOrganizationId());
            if (org.getType() == OrganizationType.courier_service
                    && accessControl.isServiceStaffForCourierService(userId, org.getId())) {
                return Optional.of(org.getId());
            }
        }
        return Optional.empty();
    }

    private Optional<UUID> findRestaurantIdForUser(Long userId) {
        List<OrganizationMember> members = memberRepository.findByUserIdAndStatus(userId, MemberStatus.active);
        for (OrganizationMember member : members) {
            Organization org = accessControl.requireOrganization(member.getOrganizationId());
            if (org.getType() == OrganizationType.client_restaurant) {
                return Optional.of(org.getId());
            }
        }
        return Optional.empty();
    }

    public Optional<OrganizationMember> findActiveCourierMembership(Long userId, UUID courierServiceId) {
        return memberRepository.findByOrganizationIdAndUserId(courierServiceId, userId)
                .filter(m -> m.getStatus() == MemberStatus.active && m.getRole() == MemberRole.courier);
    }

    public void requireEditable(DeliveryOrder order) {
        if (order.getStatus() != OrderStatus.waiting_for_courier || order.getCourierUserId() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Заказ нельзя редактировать");
        }
    }

    public void requireActiveCourierForAccept(Long userId, DeliveryOrder order) {
        profileService.requireActiveAccount(userId);
        OrganizationMember member = findActiveCourierMembership(userId, order.getCourierServiceId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "Нет курьерского доступа"));
        if (member.getStatus() != MemberStatus.active) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Курьер заблокирован");
        }
    }

    public void requireCourierOwnsOrder(Long userId, DeliveryOrder order) {
        if (order.getCourierUserId() == null || !order.getCourierUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Заказ принадлежит другому курьеру");
        }
    }

    public boolean isRestaurantStaffForOrder(Long userId, DeliveryOrder order) {
        Organization restaurant = accessControl.requireRestaurant(order.getRestaurantId());
        return accessControl.canManageRestaurantResource(userId, restaurant)
                || accessControl.canViewOrganization(userId, restaurant);
    }

    public boolean isRestaurantManagerForOrder(Long userId, DeliveryOrder order) {
        Organization restaurant = accessControl.requireRestaurant(order.getRestaurantId());
        return accessControl.canManageRestaurantResource(userId, restaurant);
    }

    public void requireCancellable(DeliveryOrder order) {
        if (order.getStatus() == OrderStatus.completed) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Выполненный заказ нельзя отменить");
        }
        if (order.getStatus() == OrderStatus.cancelled) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Заказ уже отменён");
        }
    }

    public void requireRepublishable(DeliveryOrder order) {
        if (order.getStatus() == OrderStatus.cancelled) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Отменённый заказ нельзя опубликовать повторно");
        }
        if (order.getStatus() == OrderStatus.completed) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Выполненный заказ нельзя опубликовать повторно");
        }
        if (order.getStatus() != OrderStatus.waiting_for_courier || order.getCourierUserId() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Заказ нельзя опубликовать повторно");
        }
    }
}
