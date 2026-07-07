package ru.kzn.buzanov.delivery.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.kzn.buzanov.delivery.domain.PartnerProgramRule;
import ru.kzn.buzanov.delivery.domain.PartnerReferralType;
import ru.kzn.buzanov.delivery.domain.PartnerReferrerType;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PartnerProgramRuleRepository extends JpaRepository<PartnerProgramRule, UUID> {

    List<PartnerProgramRule> findByCourierServiceIdOrderByReferrerTypeAscInviteeTypeAsc(UUID courierServiceId);

    Optional<PartnerProgramRule> findByCourierServiceIdAndReferrerTypeAndInviteeType(
            UUID courierServiceId,
            PartnerReferrerType referrerType,
            PartnerReferralType inviteeType);

    List<PartnerProgramRule> findAllByCourierServiceIdAndReferrerTypeAndInviteeType(
            UUID courierServiceId,
            PartnerReferrerType referrerType,
            PartnerReferralType inviteeType);
}
