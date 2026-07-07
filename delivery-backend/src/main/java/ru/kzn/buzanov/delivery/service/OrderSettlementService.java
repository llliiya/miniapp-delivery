package ru.kzn.buzanov.delivery.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.kzn.buzanov.delivery.api.OrderConflictException;
import ru.kzn.buzanov.delivery.domain.CourierServiceFinancialSettings;
import ru.kzn.buzanov.delivery.domain.DeliveryOrder;
import ru.kzn.buzanov.delivery.domain.OrderFinancialSnapshot;
import ru.kzn.buzanov.delivery.repository.BalanceTransactionRepository;
import ru.kzn.buzanov.delivery.domain.BalanceTransactionType;
import ru.kzn.buzanov.delivery.repository.OrderFinancialSnapshotRepository;
import ru.kzn.buzanov.delivery.util.PartnerJsonMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderSettlementService {

    private final OrderFinancialSnapshotRepository snapshotRepository;
    private final BalanceTransactionRepository balanceTransactionRepository;
    private final CourierServiceFinancialSettingsService financialSettingsService;
    private final PartnerAccrualService partnerAccrualService;
    private final CourierBalanceService courierBalanceService;
    private final PartnerJsonMapper jsonMapper;

    @Transactional
    public void settleCompletedOrder(DeliveryOrder order) {
        UUID orderId = order.getId();
        if (snapshotRepository.existsByOrderId(orderId)
                || balanceTransactionRepository.existsByOrderIdAndType(
                        orderId, BalanceTransactionType.ORDER_COMPLETED)) {
            return;
        }

        Instant now = Instant.now();
        BigDecimal deliveryPrice = order.getPrice() != null ? order.getPrice() : BigDecimal.ZERO;

        CourierServiceFinancialSettings settings =
                financialSettingsService.resolveSettings(order.getCourierServiceId());
        BigDecimal platformFeeAmount = PlatformFeeCalculator.calculateAmount(settings, deliveryPrice);

        List<PlannedPartnerAccrual> planned = partnerAccrualService.planForOrder(order, now);
        BigDecimal partnerRewardAmount = planned.stream()
                .map(PlannedPartnerAccrual::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal courierNetEarning = deliveryPrice
                .subtract(platformFeeAmount)
                .subtract(partnerRewardAmount);
        if (courierNetEarning.signum() < 0) {
            throw new OrderConflictException(
                    "order_settlement_negative",
                    "Сумма удержаний ("
                            + platformFeeAmount.add(partnerRewardAmount).toPlainString()
                            + " ₽) превышает стоимость доставки ("
                            + deliveryPrice.toPlainString()
                            + " ₽)");
        }

        UUID partnerRuleId = planned.isEmpty() ? null : planned.getFirst().ruleId();

        OrderFinancialSnapshot snapshot = new OrderFinancialSnapshot();
        snapshot.setId(UUID.randomUUID());
        snapshot.setOrderId(orderId);
        snapshot.setDeliveryPrice(deliveryPrice);
        snapshot.setPlatformFeeEnabled(settings.isPlatformFeeEnabled());
        snapshot.setPlatformFeeType(settings.getPlatformFeeType());
        snapshot.setPlatformFeeValue(settings.getPlatformFeeValue());
        snapshot.setPlatformFeeAmount(platformFeeAmount);
        snapshot.setPartnerRewardAmount(partnerRewardAmount);
        snapshot.setCourierNetEarning(courierNetEarning);
        snapshot.setPartnerRuleId(partnerRuleId);
        snapshot.setDetailsSnapshot(jsonMapper.toJson(buildDetailsSnapshot(settings, planned)));
        snapshot.setCreatedAt(now);

        try {
            snapshotRepository.save(snapshot);
        } catch (DataIntegrityViolationException ex) {
            return;
        }

        if (courierNetEarning.signum() > 0) {
            courierBalanceService.creditOrderNetEarning(order, courierNetEarning);
        }
        partnerAccrualService.applyPlannedAccruals(order, planned, now);
    }

    private Map<String, Object> buildDetailsSnapshot(
            CourierServiceFinancialSettings settings,
            List<PlannedPartnerAccrual> planned) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("platformFeeEnabled", settings.isPlatformFeeEnabled());
        details.put("platformFeeType", settings.getPlatformFeeType() != null
                ? settings.getPlatformFeeType().name()
                : null);
        details.put("platformFeeValue", settings.getPlatformFeeValue());

        List<Map<String, Object>> partnerLines = new ArrayList<>();
        for (PlannedPartnerAccrual line : planned) {
            Map<String, Object> item = new HashMap<>();
            item.put("referralId", line.referral().getId());
            item.put("partnerRuleId", line.rule().getId());
            item.put("referrerType", line.referral().getReferrerType().name());
            item.put("inviteeType", line.referral().getInviteeType().name());
            item.put("calculationType", line.rule().getCalculationType().name());
            item.put("calculationBase", line.rule().getCalculationBase().name());
            item.put("percentValue", line.rule().getPercentValue());
            item.put("fixedAmount", line.rule().getFixedAmount());
            item.put("calculationBaseAmount", line.calculationBaseAmount());
            item.put("amount", line.amount());
            partnerLines.add(item);
        }
        details.put("partnerAccruals", partnerLines);
        return details;
    }
}
