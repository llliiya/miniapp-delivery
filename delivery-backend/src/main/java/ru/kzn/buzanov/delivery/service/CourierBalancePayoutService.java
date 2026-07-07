package ru.kzn.buzanov.delivery.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.kzn.buzanov.delivery.api.CourierBalancePayoutConflictException;
import ru.kzn.buzanov.delivery.api.PartnerPayoutConflictException;
import ru.kzn.buzanov.delivery.domain.BalanceTransaction;
import ru.kzn.buzanov.delivery.domain.BalanceTransactionType;
import ru.kzn.buzanov.delivery.domain.CourierBalancePayoutMethod;
import ru.kzn.buzanov.delivery.domain.CourierBalancePayoutRequest;
import ru.kzn.buzanov.delivery.domain.CourierBalancePayoutStatus;
import ru.kzn.buzanov.delivery.domain.CourierProfile;
import ru.kzn.buzanov.delivery.domain.DeliveryOrder;
import ru.kzn.buzanov.delivery.domain.MemberRole;
import ru.kzn.buzanov.delivery.domain.OrganizationMember;
import ru.kzn.buzanov.delivery.domain.PartnerParticipantType;
import ru.kzn.buzanov.delivery.domain.PartnerPayoutMethod;
import ru.kzn.buzanov.delivery.domain.PartnerPayoutStatus;
import ru.kzn.buzanov.delivery.domain.PartnerPayoutTransferType;
import ru.kzn.buzanov.delivery.dto.BalanceTransactionDto;
import ru.kzn.buzanov.delivery.dto.CourierBalancePayoutRequestDto;
import ru.kzn.buzanov.delivery.dto.CourierBalanceSummaryDto;
import ru.kzn.buzanov.delivery.dto.PartnerPayoutDetailsDto;
import ru.kzn.buzanov.delivery.dto.PartnerPayoutRequestAdminDto;
import ru.kzn.buzanov.delivery.dto.request.CreateCourierBalancePayoutRequest;
import ru.kzn.buzanov.delivery.repository.BalanceTransactionRepository;
import ru.kzn.buzanov.delivery.repository.CourierBalancePayoutRequestRepository;
import ru.kzn.buzanov.delivery.repository.CourierProfileRepository;
import ru.kzn.buzanov.delivery.repository.DeliveryOrderRepository;
import ru.kzn.buzanov.delivery.repository.OrganizationMemberRepository;
import ru.kzn.buzanov.delivery.util.PartnerJsonMapper;
import ru.kzn.buzanov.delivery.util.PartnerPayoutDetailsSupport;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CourierBalancePayoutService {

    private static final String BALANCE_SOURCE_MAIN = "MAIN";

    private static final Set<CourierBalancePayoutStatus> RESERVED_STATUSES = EnumSet.of(
            CourierBalancePayoutStatus.PENDING,
            CourierBalancePayoutStatus.SCHEDULED,
            CourierBalancePayoutStatus.PROCESSING);

    private static final Set<CourierBalancePayoutStatus> PROCESSABLE_STATUSES = RESERVED_STATUSES;

    private final CourierBalancePayoutRequestRepository payoutRepository;
    private final CourierProfileRepository courierProfileRepository;
    private final OrganizationMemberRepository memberRepository;
    private final BalanceTransactionRepository balanceTransactionRepository;
    private final DeliveryOrderRepository orderRepository;
    private final AccessControlService accessControl;
    private final PartnerJsonMapper jsonMapper;

    @Transactional(readOnly = true)
    public CourierBalanceSummaryDto getSummary(Long userId, UUID memberId) {
        OrganizationMember member = requireCourierMember(userId, memberId);
        CourierProfile profile = requireProfile(member.getId());
        return buildSummary(profile);
    }

    @Transactional(readOnly = true)
    public List<PartnerPayoutRequestAdminDto> listAdminPayouts(UUID courierServiceId) {
        return payoutRepository.findByCourierServiceIdOrderByCreatedAtDesc(courierServiceId).stream()
                .map(this::toAdminDto)
                .toList();
    }

    @Transactional
    public CourierBalancePayoutRequestDto createPayout(
            Long userId,
            UUID memberId,
            CreateCourierBalancePayoutRequest request) {
        OrganizationMember member = requireCourierMember(userId, memberId);
        CourierProfile profile = requireProfile(member.getId());

        if (request.payoutMethod() != CourierBalancePayoutMethod.BANK_TRANSFER) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Способ выплаты недоступен");
        }

        if (payoutRepository.existsByCourierMemberIdAndStatusIn(member.getId(), RESERVED_STATUSES)) {
            throw new CourierBalancePayoutConflictException(
                    "courier_payout_pending_exists",
                    "У вас уже есть активная заявка на выплату",
                    "amount",
                    HttpStatus.CONFLICT);
        }

        validateBankPayoutDetails(request.payoutDetails());

        if (request.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new CourierBalancePayoutConflictException(
                    "courier_payout_invalid_amount",
                    "Сумма должна быть больше 0",
                    "amount",
                    HttpStatus.BAD_REQUEST);
        }

        BigDecimal available = computeAvailableForPayout(profile);
        if (request.amount().compareTo(available) > 0) {
            throw new CourierBalancePayoutConflictException(
                    "courier_payout_insufficient_funds",
                    "Недостаточно средств для выплаты",
                    "amount",
                    HttpStatus.BAD_REQUEST);
        }

        Instant now = Instant.now();
        CourierBalancePayoutRequest payout = new CourierBalancePayoutRequest();
        payout.setId(UUID.randomUUID());
        payout.setCourierMemberId(member.getId());
        payout.setAmount(request.amount());
        payout.setPayoutMethod(request.payoutMethod());
        payout.setStatus(CourierBalancePayoutStatus.PENDING);
        payout.setPayoutDetails(jsonMapper.toJson(PartnerPayoutDetailsSupport.toMap(request.payoutDetails())));
        payout.setCreatedAt(now);
        payout.setUpdatedAt(now);

        return toDto(payoutRepository.save(payout));
    }

    @Transactional
    public CourierBalancePayoutRequestDto processPayout(
            Long userId,
            UUID courierServiceId,
            UUID payoutRequestId,
            boolean approve,
            String comment) {
        accessControl.requireServiceStaff(userId, courierServiceId);

        CourierBalancePayoutRequest payout = payoutRepository.findById(payoutRequestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Заявка не найдена"));

        OrganizationMember member = memberRepository.findById(payout.getCourierMemberId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Курьер не найден"));
        if (!member.getOrganizationId().equals(courierServiceId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Нет доступа");
        }

        if (!PROCESSABLE_STATUSES.contains(payout.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Заявка уже обработана");
        }

        Instant now = Instant.now();
        if (!approve) {
            if (comment == null || comment.isBlank()) {
                throw new ru.kzn.buzanov.delivery.api.PartnerPayoutConflictException(
                        "partner_payout_rejection_comment_required",
                        null,
                        HttpStatus.BAD_REQUEST);
            }
            payout.setStatus(CourierBalancePayoutStatus.REJECTED);
            payout.setProcessedAt(now);
            payout.setProcessedBy(userId);
            payout.setUpdatedAt(now);
            appendRejectionComment(payout, comment.trim());
            return toDto(payoutRepository.save(payout));
        }

        if (!hasCompletePayoutDetails(payout)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Заявка не готова к выплате: отсутствуют банковские реквизиты");
        }

        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        if (!PartnerPayoutCycle.canConfirmPayout(today, payout.getScheduledPayoutDate())) {
            throw new ru.kzn.buzanov.delivery.api.PartnerPayoutConflictException("partner_payout_date_not_reached");
        }

        CourierProfile profile = requireProfile(member.getId());
        requireReservedPayoutAmount(profile, payout);

        profile.setBalance(profile.getBalance().subtract(payout.getAmount()));
        profile.setUpdatedAt(now);
        courierProfileRepository.save(profile);

        BalanceTransaction transaction = new BalanceTransaction();
        transaction.setId(UUID.randomUUID());
        transaction.setCourierMemberId(member.getId());
        transaction.setAmount(payout.getAmount().negate());
        transaction.setType(BalanceTransactionType.PAYOUT_COMPLETED);
        transaction.setReason("Выплата по заявке " + payout.getId());
        transaction.setCreatedAt(now);
        balanceTransactionRepository.save(transaction);

        payout.setStatus(CourierBalancePayoutStatus.PAID);
        payout.setProcessedAt(now);
        payout.setProcessedBy(userId);
        payout.setUpdatedAt(now);
        return toDto(payoutRepository.save(payout));
    }

    private CourierBalanceSummaryDto buildSummary(CourierProfile profile) {
        UUID memberId = profile.getMemberId();
        BigDecimal pending = payoutRepository.sumAmountByCourierMemberIdAndStatusIn(memberId, RESERVED_STATUSES);
        if (pending == null) {
            pending = BigDecimal.ZERO;
        }
        BigDecimal paidOut = payoutRepository.sumAmountByCourierMemberIdAndStatusIn(
                memberId, EnumSet.of(CourierBalancePayoutStatus.PAID));
        if (paidOut == null) {
            paidOut = BigDecimal.ZERO;
        }
        BigDecimal available = profile.getBalance().subtract(pending).max(BigDecimal.ZERO);
        boolean hasPendingPayout = payoutRepository.existsByCourierMemberIdAndStatusIn(memberId, RESERVED_STATUSES);
        boolean canCreatePayoutRequest = available.compareTo(BigDecimal.ZERO) > 0 && !hasPendingPayout;

        List<CourierBalancePayoutRequestDto> history =
                payoutRepository.findByCourierMemberIdOrderByCreatedAtDesc(memberId).stream()
                        .map(this::toDto)
                        .toList();

        List<BalanceTransactionDto> earningHistory = loadEarningHistory(memberId);

        return new CourierBalanceSummaryDto(
                profile.getBalance(),
                available,
                pending,
                paidOut,
                canCreatePayoutRequest,
                history,
                earningHistory);
    }

    private List<BalanceTransactionDto> loadEarningHistory(UUID memberId) {
        List<BalanceTransaction> transactions = balanceTransactionRepository
                .findByCourierMemberIdAndTypeOrderByCreatedAtDesc(memberId, BalanceTransactionType.ORDER_COMPLETED);
        if (transactions.isEmpty()) {
            return List.of();
        }
        Set<UUID> orderIds = transactions.stream()
                .map(BalanceTransaction::getOrderId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, DeliveryOrder> ordersById = orderIds.isEmpty()
                ? Map.of()
                : orderRepository.findAllById(orderIds).stream()
                        .collect(Collectors.toMap(DeliveryOrder::getId, Function.identity()));

        return transactions.stream()
                .map(tx -> new BalanceTransactionDto(
                        tx.getId(),
                        tx.getAmount(),
                        tx.getType(),
                        tx.getOrderId(),
                        tx.getOrderId() != null && ordersById.get(tx.getOrderId()) != null
                                ? ordersById.get(tx.getOrderId()).getPublicNumber()
                                : null,
                        tx.getCreatedAt()))
                .toList();
    }

    private BigDecimal computeAvailableForPayout(CourierProfile profile) {
        BigDecimal pending = sumReservedPayoutAmount(profile.getMemberId());
        return profile.getBalance().subtract(pending).max(BigDecimal.ZERO);
    }

    private BigDecimal sumReservedPayoutAmount(UUID memberId) {
        BigDecimal pending = payoutRepository.sumAmountByCourierMemberIdAndStatusIn(memberId, RESERVED_STATUSES);
        return pending != null ? pending : BigDecimal.ZERO;
    }

    private void requireReservedPayoutAmount(CourierProfile profile, CourierBalancePayoutRequest payout) {
        if (!RESERVED_STATUSES.contains(payout.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Сумма заявки не зарезервирована");
        }
        if (profile.getBalance().compareTo(payout.getAmount()) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Сумма заявки превышает баланс курьера");
        }
    }

    private void validateBankPayoutDetails(PartnerPayoutDetailsDto details) {
        try {
            PartnerPayoutDetailsSupport.requireForBankTransfer(details);
        } catch (PartnerPayoutConflictException ex) {
            throw detailsRequired(ex.getConflictField(), ex.getUserMessage());
        }
    }

    private CourierBalancePayoutConflictException detailsRequired(String field, String message) {
        return new CourierBalancePayoutConflictException(
                "courier_payout_details_required",
                message,
                field,
                HttpStatus.BAD_REQUEST);
    }

    private OrganizationMember requireCourierMember(Long userId, UUID memberId) {
        OrganizationMember member = memberRepository.findById(memberId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Курьер не найден"));
        if (member.getRole() != MemberRole.courier) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Участник не является курьером");
        }
        accessControl.requireActiveMembership(userId, member.getOrganizationId());
        if (!member.getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Нет доступа");
        }
        return member;
    }

    private CourierProfile requireProfile(UUID memberId) {
        return courierProfileRepository.findByMemberId(memberId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Профиль курьера не найден"));
    }

    private CourierBalancePayoutRequestDto toDto(CourierBalancePayoutRequest payout) {
        Map<String, Object> details = jsonMapper.toMap(payout.getPayoutDetails());
        return new CourierBalancePayoutRequestDto(
                payout.getId(),
                payout.getAmount(),
                payout.getPayoutMethod(),
                payout.getStatus(),
                payout.getScheduledPayoutDate(),
                payout.getCreatedAt(),
                payout.getProcessedAt(),
                PartnerPayoutDetailsSupport.resolveTransferType(details),
                PartnerPayoutDetailsSupport.maskRequisites(details),
                PartnerPayoutDetailsSupport.readRecipientName(details),
                PartnerPayoutDetailsSupport.readRejectionComment(details));
    }

    private PartnerPayoutRequestAdminDto toAdminDto(CourierBalancePayoutRequest payout) {
        OrganizationMember member = memberRepository.findById(payout.getCourierMemberId()).orElse(null);
        Map<String, Object> details = jsonMapper.toMap(payout.getPayoutDetails());
        PartnerPayoutTransferType transferType = PartnerPayoutDetailsSupport.resolveTransferType(details);
        String cardNumber = transferType == PartnerPayoutTransferType.CARD
                ? PartnerPayoutDetailsSupport.readCardNumber(details)
                : null;
        String phoneNumber = transferType == PartnerPayoutTransferType.SBP_PHONE
                ? PartnerPayoutDetailsSupport.readPhoneNumber(details)
                : null;
        String recipientName = PartnerPayoutDetailsSupport.readRecipientName(details);
        String participantName = member != null && member.getDisplayName() != null
                ? member.getDisplayName()
                : "Курьер";
        return new PartnerPayoutRequestAdminDto(
                payout.getId(),
                null,
                PartnerParticipantType.COURIER,
                participantName,
                payout.getCourierMemberId(),
                member != null ? member.getOrganizationId() : null,
                BALANCE_SOURCE_MAIN,
                payout.getAmount(),
                PartnerPayoutMethod.BANK_TRANSFER,
                mapStatus(payout.getStatus()),
                payout.getScheduledPayoutDate(),
                payout.getCreatedAt(),
                payout.getProcessedAt(),
                transferType,
                cardNumber,
                phoneNumber,
                recipientName,
                PartnerPayoutDetailsSupport.readBankName(details),
                PartnerPayoutDetailsSupport.hasCompletePayoutDetails(details));
    }

    private PartnerPayoutStatus mapStatus(CourierBalancePayoutStatus status) {
        return switch (status) {
            case PENDING -> PartnerPayoutStatus.PENDING;
            case SCHEDULED -> PartnerPayoutStatus.SCHEDULED;
            case PROCESSING -> PartnerPayoutStatus.PROCESSING;
            case PAID -> PartnerPayoutStatus.PAID;
            case REJECTED -> PartnerPayoutStatus.REJECTED;
            case CANCELLED -> PartnerPayoutStatus.CANCELLED;
        };
    }

    private boolean hasCompletePayoutDetails(CourierBalancePayoutRequest payout) {
        return PartnerPayoutDetailsSupport.hasRequiredBankDetails(jsonMapper.toMap(payout.getPayoutDetails()));
    }

    private void appendRejectionComment(CourierBalancePayoutRequest payout, String comment) {
        Map<String, Object> details = new LinkedHashMap<>(jsonMapper.toMap(payout.getPayoutDetails()));
        details.put("rejectionComment", comment);
        payout.setPayoutDetails(jsonMapper.toJson(details));
    }
}
