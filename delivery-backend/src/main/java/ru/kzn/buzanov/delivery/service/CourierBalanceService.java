package ru.kzn.buzanov.delivery.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.kzn.buzanov.delivery.domain.BalanceTransaction;
import ru.kzn.buzanov.delivery.domain.BalanceTransactionType;
import ru.kzn.buzanov.delivery.domain.CourierProfile;
import ru.kzn.buzanov.delivery.domain.DeliveryOrder;
import ru.kzn.buzanov.delivery.domain.OrganizationMember;
import ru.kzn.buzanov.delivery.repository.BalanceTransactionRepository;
import ru.kzn.buzanov.delivery.repository.CourierProfileRepository;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CourierBalanceService {

    private final OrderAccessService orderAccess;
    private final BalanceTransactionRepository balanceTransactionRepository;
    private final CourierProfileRepository courierProfileRepository;

    @Transactional
    public void accrueOnOrderCompleted(DeliveryOrder order) {
        if (order.getCourierUserId() == null) {
            return;
        }

        UUID orderId = order.getId();
        if (balanceTransactionRepository.existsByOrderIdAndType(orderId, BalanceTransactionType.ORDER_COMPLETED)) {
            return;
        }

        OrganizationMember member = orderAccess
                .findActiveCourierMembership(order.getCourierUserId(), order.getCourierServiceId())
                .orElse(null);
        if (member == null) {
            return;
        }

        CourierProfile profile = courierProfileRepository.findByMemberId(member.getId()).orElse(null);
        if (profile == null) {
            return;
        }

        Instant now = Instant.now();
        BalanceTransaction transaction = new BalanceTransaction();
        transaction.setId(UUID.randomUUID());
        transaction.setCourierMemberId(member.getId());
        transaction.setAmount(order.getPrice());
        transaction.setType(BalanceTransactionType.ORDER_COMPLETED);
        transaction.setOrderId(orderId);
        transaction.setCreatedAt(now);

        try {
            balanceTransactionRepository.save(transaction);
        } catch (DataIntegrityViolationException ex) {
            return;
        }

        profile.setBalance(profile.getBalance().add(order.getPrice()));
        profile.setUpdatedAt(now);
        courierProfileRepository.save(profile);
    }
}
