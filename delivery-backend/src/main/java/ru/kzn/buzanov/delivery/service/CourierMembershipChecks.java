package ru.kzn.buzanov.delivery.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.kzn.buzanov.delivery.api.CourierConflictException;
import ru.kzn.buzanov.delivery.domain.CourierProfile;
import ru.kzn.buzanov.delivery.domain.MemberRole;
import ru.kzn.buzanov.delivery.domain.OrganizationMember;
import ru.kzn.buzanov.delivery.dto.CourierDto;
import ru.kzn.buzanov.delivery.integration.AccountUserClient;
import ru.kzn.buzanov.delivery.repository.CourierProfileRepository;
import ru.kzn.buzanov.delivery.repository.OrganizationMemberRepository;

import java.math.BigDecimal;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class CourierMembershipChecks {

    private final OrganizationMemberRepository memberRepository;
    private final CourierProfileRepository courierProfileRepository;
    private final DeliveryDtoMapper mapper;
    private final AccountUserClient accountUserClient;

    public void ensureCanAddCourier(UUID courierServiceId, Long userId) {
        var existing = memberRepository.findByOrganizationIdAndUserId(courierServiceId, userId);
        if (existing.isEmpty()) {
            log.info("Courier add: no membership for userId={} in serviceId={}", userId, courierServiceId);
            return;
        }
        OrganizationMember member = existing.get();
        if (member.getRole() == MemberRole.courier) {
            log.info("Courier add: userId={} already courier in serviceId={}, memberId={}",
                    userId, courierServiceId, member.getId());
            throw new CourierConflictException(
                    "courier_already_in_service",
                    "Курьер уже есть в вашей службе",
                    toCourierDto(member, courierServiceId));
        }
        log.warn("Courier add: userId={} already member with role={} in serviceId={}",
                userId, member.getRole(), courierServiceId);
        throw new CourierConflictException(
                "member_other_role",
                "Пользователь уже состоит в службе с другой ролью",
                "membership");
    }

    public CourierDto toCourierDto(OrganizationMember member, UUID courierServiceId) {
        CourierProfile profile = courierProfileRepository.findByMemberId(member.getId())
                .orElseGet(() -> {
                    CourierProfile p = new CourierProfile();
                    p.setBalance(BigDecimal.ZERO);
                    p.setCompletedOrdersCount(0);
                    return p;
                });
        CourierDto base = mapper.toCourierDto(member, profile, courierServiceId);
        return accountUserClient.findUserContacts(member.getUserId())
                .map(contacts -> base.withContacts(contacts.email(), contacts.phone()))
                .orElse(base);
    }
}
