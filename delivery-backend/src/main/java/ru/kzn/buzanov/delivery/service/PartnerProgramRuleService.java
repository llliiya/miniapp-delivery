package ru.kzn.buzanov.delivery.service;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.kzn.buzanov.delivery.domain.PartnerCalculationBase;
import ru.kzn.buzanov.delivery.domain.PartnerCalculationType;
import ru.kzn.buzanov.delivery.domain.PartnerProgramRule;
import ru.kzn.buzanov.delivery.domain.PartnerPayoutMethod;
import ru.kzn.buzanov.delivery.domain.PartnerReferralType;
import ru.kzn.buzanov.delivery.domain.PartnerReferrerType;
import ru.kzn.buzanov.delivery.dto.PartnerProgramRuleDto;
import ru.kzn.buzanov.delivery.dto.PartnerRuleSnapshotDto;
import ru.kzn.buzanov.delivery.dto.request.UpsertPartnerProgramRuleRequest;
import ru.kzn.buzanov.delivery.repository.PartnerProgramRuleRepository;
import ru.kzn.buzanov.delivery.util.PartnerJsonMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PartnerProgramRuleService {

    private final PartnerProgramRuleRepository ruleRepository;
    private final AccessControlService accessControl;
    private final PartnerJsonMapper jsonMapper;

    @Transactional(readOnly = true)
    public List<PartnerProgramRuleDto> listForService(Long userId, UUID courierServiceId) {
        accessControl.requireServiceStaff(userId, courierServiceId);
        return ruleRepository.findByCourierServiceIdOrderByReferrerTypeAscInviteeTypeAsc(courierServiceId).stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public PartnerProgramRuleDto upsert(Long userId, UUID courierServiceId, UpsertPartnerProgramRuleRequest request) {
        accessControl.requireServiceStaff(userId, courierServiceId);
        validateRequest(request);

        List<PartnerProgramRule> existingRules = ruleRepository
                .findAllByCourierServiceIdAndReferrerTypeAndInviteeType(
                        courierServiceId, request.referrerType(), request.inviteeType());
        PartnerProgramRule rule = existingRules.stream()
                .max(Comparator.comparing(
                        existing -> existing.getUpdatedAt() != null ? existing.getUpdatedAt() : existing.getCreatedAt()))
                .orElseGet(() -> {
                    PartnerProgramRule created = new PartnerProgramRule();
                    created.setId(UUID.randomUUID());
                    created.setCourierServiceId(courierServiceId);
                    created.setReferrerType(request.referrerType());
                    created.setInviteeType(request.inviteeType());
                    created.setCreatedAt(Instant.now());
                    return created;
                });

        if (existingRules.size() > 1) {
            UUID keepId = rule.getId();
            existingRules.stream()
                    .filter(existing -> !existing.getId().equals(keepId))
                    .forEach(ruleRepository::delete);
        }

        Instant now = Instant.now();
        PartnerCalculationBase calculationBase = resolveCalculationBase(request);
        PartnerCalculationType calculationType = calculationBase == PartnerCalculationBase.FIXED_PER_DELIVERY
                ? PartnerCalculationType.FIXED
                : PartnerCalculationType.PERCENT;
        rule.setEnabled(request.enabled());
        rule.setCalculationType(calculationType);
        rule.setPercentValue(request.percentValue());
        rule.setFixedAmount(request.fixedAmount());
        rule.setCalculationBase(calculationBase);
        rule.setDurationMonths(request.durationMonths());
        rule.setEffectiveFrom(request.effectiveFrom());
        rule.setAccrualConditions(jsonMapper.toJson(request.accrualConditions()));
        rule.setPayoutRestrictions(jsonMapper.toJson(request.payoutRestrictions()));
        rule.setMinPayoutAmount(request.minPayoutAmount() != null ? request.minPayoutAmount() : BigDecimal.ZERO);
        rule.setPayoutMethods(jsonMapper.toJson(request.payoutMethods().stream().map(Enum::name).toList()));
        rule.setUpdatedAt(now);
        try {
            return toDto(ruleRepository.save(rule));
        } catch (DataIntegrityViolationException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Конфликт сохранения правила. Обновите страницу и попробуйте снова.");
        }
    }

    @Transactional(readOnly = true)
    public PartnerProgramRule findActiveRule(
            UUID courierServiceId,
            PartnerReferrerType referrerType,
            PartnerReferralType inviteeType) {
        return ruleRepository
                .findAllByCourierServiceIdAndReferrerTypeAndInviteeType(courierServiceId, referrerType, inviteeType)
                .stream()
                .filter(this::isRuleActive)
                .max(Comparator.comparing(
                        rule -> rule.getUpdatedAt() != null ? rule.getUpdatedAt() : rule.getCreatedAt()))
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public boolean isEnabledForService(UUID courierServiceId) {
        return ruleRepository.findByCourierServiceIdOrderByReferrerTypeAscInviteeTypeAsc(courierServiceId).stream()
                .anyMatch(this::isRuleActive);
    }

    @Transactional(readOnly = true)
    public boolean isEnabledForReferrer(UUID courierServiceId, PartnerReferrerType referrerType) {
        return ruleRepository.findByCourierServiceIdOrderByReferrerTypeAscInviteeTypeAsc(courierServiceId).stream()
                .filter(rule -> rule.getReferrerType() == referrerType)
                .anyMatch(this::isRuleActive);
    }

    private boolean isRuleActive(PartnerProgramRule rule) {
        return rule.isEnabled() && !rule.getEffectiveFrom().isAfter(LocalDate.now());
    }

    public PartnerRuleSnapshotDto toSnapshot(PartnerProgramRule rule) {
        return new PartnerRuleSnapshotDto(
                rule.getId(),
                rule.getCourierServiceId(),
                rule.getReferrerType(),
                rule.getInviteeType(),
                rule.isEnabled(),
                rule.getCalculationType(),
                rule.getPercentValue(),
                rule.getFixedAmount(),
                rule.getCalculationBase(),
                rule.getDurationMonths(),
                rule.getEffectiveFrom(),
                jsonMapper.toMap(rule.getAccrualConditions()),
                jsonMapper.toMap(rule.getPayoutRestrictions()),
                rule.getMinPayoutAmount(),
                jsonMapper.toStringList(rule.getPayoutMethods()));
    }

    private void validateRequest(UpsertPartnerProgramRuleRequest request) {
        PartnerCalculationBase calculationBase = resolveCalculationBase(request);
        if (calculationBase == PartnerCalculationBase.FIXED_PER_DELIVERY) {
            if (request.fixedAmount() == null || request.fixedAmount().signum() <= 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Укажите фиксированную сумму за доставку");
            }
        } else if (request.percentValue() == null || request.percentValue().signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Укажите процент начисления");
        }
        if (request.durationMonths() != null && request.durationMonths() < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Срок действия не может быть отрицательным");
        }
        if (request.minPayoutAmount() != null && request.minPayoutAmount().signum() < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Минимальная сумма выплаты не может быть отрицательной");
        }
        Map<String, Object> payoutRestrictions = request.payoutRestrictions();
        Object payoutDay = payoutRestrictions != null ? payoutRestrictions.get("payoutDayOfMonth") : null;
        if (payoutDay instanceof Number number) {
            int day = number.intValue();
            if (day < 1 || day > 28) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "День выплаты должен быть от 1 до 28");
            }
        } else if (payoutDay != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "День выплаты должен быть числом");
        }
        if (request.payoutMethods().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Выберите хотя бы один способ выплаты");
        }
        if (request.referrerType() == PartnerReferrerType.RESTAURANT
                && request.payoutMethods().stream().anyMatch(method -> method == PartnerPayoutMethod.TRANSFER_TO_MAIN_BALANCE)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Для объектов доступен только банковский перевод");
        }
    }

    private PartnerCalculationBase resolveCalculationBase(UpsertPartnerProgramRuleRequest request) {
        if (request.calculationType() == PartnerCalculationType.FIXED
                || request.calculationBase() == PartnerCalculationBase.FIXED_PER_DELIVERY) {
            return PartnerCalculationBase.FIXED_PER_DELIVERY;
        }
        if (request.calculationBase() == PartnerCalculationBase.PLATFORM_COMMISSION
                || request.calculationBase() == PartnerCalculationBase.PLATFORM_PROFIT) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Базы расчёта «комиссия платформы» и «прибыль платформы» больше не поддерживаются");
        }
        return request.inviteeType() == PartnerReferralType.RESTAURANT
                ? PartnerCalculationBase.DELIVERY_PRICE
                : PartnerCalculationBase.COURIER_EARNING;
    }

    private PartnerProgramRuleDto toDto(PartnerProgramRule rule) {
        return new PartnerProgramRuleDto(
                rule.getId(),
                rule.getCourierServiceId(),
                rule.getReferrerType(),
                rule.getInviteeType(),
                rule.isEnabled(),
                rule.getCalculationType(),
                rule.getPercentValue(),
                rule.getFixedAmount(),
                rule.getCalculationBase(),
                rule.getDurationMonths(),
                rule.getEffectiveFrom(),
                jsonMapper.toMap(rule.getAccrualConditions()),
                jsonMapper.toMap(rule.getPayoutRestrictions()),
                rule.getMinPayoutAmount(),
                jsonMapper.toStringList(rule.getPayoutMethods()));
    }
}
