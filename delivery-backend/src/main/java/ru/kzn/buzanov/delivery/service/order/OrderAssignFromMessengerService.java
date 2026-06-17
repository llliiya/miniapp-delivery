package ru.kzn.buzanov.delivery.service.order;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.kzn.buzanov.delivery.api.OrderConflictException;
import ru.kzn.buzanov.delivery.domain.CourierRequestStatus;
import ru.kzn.buzanov.delivery.domain.DeliveryOrder;
import ru.kzn.buzanov.delivery.domain.MemberRole;
import ru.kzn.buzanov.delivery.domain.MemberStatus;
import ru.kzn.buzanov.delivery.domain.OrganizationMember;
import ru.kzn.buzanov.delivery.integration.AccountUserClient;
import ru.kzn.buzanov.delivery.repository.CourierRequestRepository;
import ru.kzn.buzanov.delivery.repository.DeliveryOrderRepository;
import ru.kzn.buzanov.delivery.repository.OrganizationMemberRepository;
import ru.kzn.buzanov.delivery.service.OrderService;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderAssignFromMessengerService {

    public static final String CALLBACK_PREFIX = "assign_order:";

    private final AccountUserClient accountUserClient;
    private final DeliveryOrderRepository orderRepository;
    private final OrganizationMemberRepository memberRepository;
    private final CourierRequestRepository courierRequestRepository;
    private final OrderService orderService;

    public static boolean isAssignOrderCallback(String callbackData) {
        return callbackData != null && callbackData.startsWith(CALLBACK_PREFIX);
    }

    public static Optional<UUID> parseOrderIdFromCallback(String callbackData) {
        if (!isAssignOrderCallback(callbackData)) {
            return Optional.empty();
        }
        String raw = callbackData.substring(CALLBACK_PREFIX.length()).trim();
        if (raw.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(raw));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    @Transactional
    public MessengerAssignResult tryAssign(String provider, String externalUserId, UUID orderId) {
        String normalizedProvider = normalizeProvider(provider);
        String normalizedExternalId = normalizeExternalId(externalUserId);
        if (normalizedProvider.isEmpty() || normalizedExternalId.isEmpty() || orderId == null) {
            return result(MessengerAssignOutcome.ORDER_NOT_AVAILABLE, "Некорректный запрос", orderId, null);
        }

        DeliveryOrder order = orderRepository.findById(orderId).orElse(null);
        if (order == null) {
            return result(MessengerAssignOutcome.ORDER_NOT_AVAILABLE, "Заказ не найден", orderId, null);
        }

        Optional<Long> userIdOpt =
                accountUserClient.findUserIdByExternalIdentity(normalizedProvider, normalizedExternalId);
        if (userIdOpt.isEmpty()) {
            return result(
                    MessengerAssignOutcome.NOT_LINKED,
                    "Откройте мини-приложение и войдите, чтобы взять заказ",
                    orderId,
                    null);
        }

        Long userId = userIdOpt.get();
        Optional<OrganizationMember> membership =
                memberRepository.findByOrganizationIdAndUserId(order.getCourierServiceId(), userId);
        if (membership.isEmpty() || membership.get().getRole() != MemberRole.courier) {
            if (hasPendingCourierApplication(normalizedProvider, normalizedExternalId)) {
                return result(
                        MessengerAssignOutcome.PENDING,
                        "Ваша заявка курьера ожидает одобрения",
                        orderId,
                        null);
            }
            return result(
                    MessengerAssignOutcome.NOT_COURIER,
                    "Чтобы взять заказ, нужно зарегистрироваться курьером",
                    orderId,
                    null);
        }

        OrganizationMember member = membership.get();
        if (member.getStatus() == MemberStatus.blocked) {
            return result(MessengerAssignOutcome.BLOCKED, "Доступ к заказам ограничен", orderId, null);
        }
        if (member.getStatus() != MemberStatus.active) {
            return result(
                    MessengerAssignOutcome.PENDING,
                    "Ваша заявка курьера ожидает одобрения",
                    orderId,
                    null);
        }

        try {
            orderService.assign(userId, orderId, userId);
            log.info(
                    "Order {} assigned to courier {} via {} {}",
                    orderId,
                    userId,
                    normalizedProvider,
                    normalizedExternalId);
            return result(
                    MessengerAssignOutcome.ASSIGNED,
                    "Заказ назначен вам. Откройте «Мои заказы» в мини-приложении.",
                    orderId,
                    userId);
        } catch (OrderConflictException ex) {
            MessengerAssignOutcome outcome = switch (ex.getErrorCode()) {
                case "order_already_taken" -> MessengerAssignOutcome.ORDER_ALREADY_TAKEN;
                default -> MessengerAssignOutcome.ORDER_NOT_AVAILABLE;
            };
            return result(outcome, ex.getUserMessage(), orderId, null);
        } catch (ResponseStatusException ex) {
            if (ex.getStatusCode() == HttpStatus.FORBIDDEN) {
                return result(MessengerAssignOutcome.NOT_COURIER, ex.getReason(), orderId, null);
            }
            return result(MessengerAssignOutcome.ORDER_NOT_AVAILABLE, ex.getReason(), orderId, null);
        }
    }

    private boolean hasPendingCourierApplication(String provider, String externalId) {
        return courierRequestRepository
                .findFirstByMessengerProviderAndMessengerExternalIdAndStatusOrderByCreatedAtDesc(
                        provider, externalId, CourierRequestStatus.NEW)
                .isPresent();
    }

    private static MessengerAssignResult result(
            MessengerAssignOutcome outcome, String message, UUID orderId, Long courierUserId) {
        return new MessengerAssignResult(outcome, message, orderId, courierUserId);
    }

    private static String normalizeProvider(String provider) {
        if (provider == null) {
            return "";
        }
        return provider.trim().toUpperCase();
    }

    private static String normalizeExternalId(String externalId) {
        if (externalId == null) {
            return "";
        }
        return externalId.trim();
    }
}
