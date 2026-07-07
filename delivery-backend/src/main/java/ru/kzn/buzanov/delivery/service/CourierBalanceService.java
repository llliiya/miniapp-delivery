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



import java.math.BigDecimal;

import java.time.Instant;

import java.util.UUID;



@Service

@RequiredArgsConstructor

public class CourierBalanceService {



    private final OrderAccessService orderAccess;

    private final BalanceTransactionRepository balanceTransactionRepository;

    private final CourierProfileRepository courierProfileRepository;



    @Transactional

    public void creditOrderNetEarning(DeliveryOrder order, BigDecimal netAmount) {

        if (order.getCourierUserId() == null || netAmount == null || netAmount.signum() <= 0) {

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

        transaction.setAmount(netAmount);

        transaction.setType(BalanceTransactionType.ORDER_COMPLETED);

        transaction.setOrderId(orderId);

        transaction.setCreatedAt(now);



        try {

            balanceTransactionRepository.save(transaction);

        } catch (DataIntegrityViolationException ex) {

            return;

        }



        profile.setBalance(profile.getBalance().add(netAmount));

        profile.setUpdatedAt(now);

        courierProfileRepository.save(profile);

    }



    @Transactional
    public UUID creditMainBalanceFromPartnerTransfer(UUID memberId, BigDecimal amount, UUID balanceTransferId) {
        if (amount == null || amount.signum() <= 0) {
            return null;
        }

        CourierProfile profile = courierProfileRepository.findByMemberId(memberId).orElse(null);
        if (profile == null) {
            return null;
        }

        Instant now = Instant.now();
        profile.setBalance(profile.getBalance().add(amount));
        profile.setUpdatedAt(now);
        courierProfileRepository.save(profile);

        BalanceTransaction transaction = new BalanceTransaction();
        UUID transactionId = UUID.randomUUID();
        transaction.setId(transactionId);
        transaction.setCourierMemberId(memberId);
        transaction.setAmount(amount);
        transaction.setType(BalanceTransactionType.PARTNER_TRANSFER_IN);
        transaction.setReason("Перевод на общий баланс"
                + (balanceTransferId != null ? " (" + balanceTransferId + ")" : ""));
        transaction.setCreatedAt(now);
        balanceTransactionRepository.save(transaction);
        return transactionId;
    }

    @Transactional
    public void creditMainBalance(UUID memberId, BigDecimal amount) {
        creditMainBalanceFromPartnerTransfer(memberId, amount, null);
    }

}


