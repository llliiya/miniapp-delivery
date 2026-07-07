package ru.kzn.buzanov.delivery.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.kzn.buzanov.delivery.domain.MemberStatus;
import ru.kzn.buzanov.delivery.domain.Organization;
import ru.kzn.buzanov.delivery.domain.OrganizationMember;
import ru.kzn.buzanov.delivery.domain.PartnerProgramRule;
import ru.kzn.buzanov.delivery.domain.PartnerReferral;
import ru.kzn.buzanov.delivery.domain.PartnerReferralJournalStatus;
import ru.kzn.buzanov.delivery.domain.PartnerReferralType;
import ru.kzn.buzanov.delivery.domain.PartnerReferrerType;
import ru.kzn.buzanov.delivery.dto.PartnerReferralAdminDto;
import ru.kzn.buzanov.delivery.repository.CourierRequestRepository;
import ru.kzn.buzanov.delivery.repository.OrganizationMemberRepository;
import ru.kzn.buzanov.delivery.repository.OrganizationRepository;
import ru.kzn.buzanov.delivery.repository.PartnerAccrualRepository;
import ru.kzn.buzanov.delivery.repository.PartnerProgramRuleRepository;
import ru.kzn.buzanov.delivery.repository.PartnerReferralAccrualStats;
import ru.kzn.buzanov.delivery.repository.PartnerReferralRepository;
import ru.kzn.buzanov.delivery.repository.RestaurantRegistrationRequestRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PartnerReferralAdminServiceTest {

    @Mock
    private PartnerReferralRepository referralRepository;
    @Mock
    private PartnerAccrualRepository accrualRepository;
    @Mock
    private PartnerProgramRuleRepository ruleRepository;
    @Mock
    private OrganizationMemberRepository memberRepository;
    @Mock
    private OrganizationRepository organizationRepository;
    @Mock
    private CourierRequestRepository courierRequestRepository;
    @Mock
    private RestaurantRegistrationRequestRepository restaurantRequestRepository;
    @Mock
    private AccessControlService accessControl;

    @InjectMocks
    private PartnerReferralAdminService partnerReferralAdminService;

    @Test
    void listForServiceRequiresServiceStaffAndAggregatesAccruals() {
        UUID serviceId = UUID.randomUUID();
        UUID referralId = UUID.randomUUID();
        UUID referrerMemberId = UUID.randomUUID();
        UUID inviteeMemberId = UUID.randomUUID();

        PartnerReferral referral = new PartnerReferral();
        referral.setId(referralId);
        referral.setCourierServiceId(serviceId);
        referral.setReferrerType(PartnerReferrerType.COURIER);
        referral.setReferrerMemberId(referrerMemberId);
        referral.setInviteeType(PartnerReferralType.COURIER);
        referral.setInviteeMemberId(inviteeMemberId);
        referral.setCreatedAt(Instant.parse("2026-03-01T10:00:00Z"));
        referral.setConnectedAt(Instant.parse("2026-03-02T10:00:00Z"));
        referral.setProgramExpiresAt(Instant.parse("2027-03-02T10:00:00Z"));

        OrganizationMember referrer = new OrganizationMember();
        referrer.setId(referrerMemberId);
        referrer.setDisplayName("Анна");
        referrer.setStatus(MemberStatus.active);

        OrganizationMember invitee = new OrganizationMember();
        invitee.setId(inviteeMemberId);
        invitee.setDisplayName("Борис");
        invitee.setStatus(MemberStatus.active);

        PartnerProgramRule rule = new PartnerProgramRule();
        rule.setReferrerType(PartnerReferrerType.COURIER);
        rule.setInviteeType(PartnerReferralType.COURIER);
        rule.setEnabled(true);

        PartnerReferralAccrualStats stats = new PartnerReferralAccrualStats() {
            @Override
            public UUID getPartnerReferralId() {
                return referralId;
            }

            @Override
            public long getAccrualCount() {
                return 3L;
            }

            @Override
            public BigDecimal getAccruedTotal() {
                return new BigDecimal("150.00");
            }

            @Override
            public BigDecimal getReversedTotal() {
                return new BigDecimal("20.00");
            }

            @Override
            public Instant getLastAccrualAt() {
                return Instant.parse("2026-03-10T12:00:00Z");
            }
        };

        doNothing().when(accessControl).requireServiceStaff(1L, serviceId);
        when(referralRepository.findByCourierServiceIdOrderByCreatedAtDesc(serviceId)).thenReturn(List.of(referral));
        when(accrualRepository.aggregateByReferralIds(any())).thenReturn(List.of(stats));
        when(memberRepository.findAllById(any())).thenReturn(List.of(referrer, invitee));
        when(organizationRepository.findByIdIn(any())).thenReturn(List.of());
        when(courierRequestRepository.findAllById(any())).thenReturn(List.of());
        when(restaurantRequestRepository.findAllById(any())).thenReturn(List.of());
        when(ruleRepository.findByCourierServiceIdOrderByReferrerTypeAscInviteeTypeAsc(serviceId))
                .thenReturn(List.of(rule));

        List<PartnerReferralAdminDto> result = partnerReferralAdminService.listForService(1L, serviceId);

        verify(accessControl).requireServiceStaff(1L, serviceId);
        assertEquals(1, result.size());
        PartnerReferralAdminDto dto = result.get(0);
        assertEquals("Курьер → Курьер", dto.relationshipLabel());
        assertEquals("Анна", dto.referrerName());
        assertEquals("Борис", dto.inviteeName());
        assertEquals(PartnerReferralJournalStatus.ACTIVE, dto.status());
        assertEquals(3L, dto.accrualCount());
        assertEquals(new BigDecimal("150.00"), dto.accruedAmount());
        assertEquals(new BigDecimal("20.00"), dto.reversedAmount());
        assertEquals(new BigDecimal("150.00"), dto.netAmount());
    }
}
