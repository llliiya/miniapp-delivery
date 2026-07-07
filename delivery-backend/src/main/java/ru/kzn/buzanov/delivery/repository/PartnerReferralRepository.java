package ru.kzn.buzanov.delivery.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.kzn.buzanov.delivery.domain.PartnerReferral;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PartnerReferralRepository extends JpaRepository<PartnerReferral, UUID> {

    List<PartnerReferral> findByInviteeMemberId(UUID inviteeMemberId);

    List<PartnerReferral> findByInviteeOrganizationId(UUID inviteeOrganizationId);

    List<PartnerReferral> findByReferrerMemberIdOrderByConnectedAtDesc(UUID referrerMemberId);

    List<PartnerReferral> findByReferrerOrganizationIdOrderByConnectedAtDesc(UUID referrerOrganizationId);

    Optional<PartnerReferral> findBySourceCourierRequestId(UUID sourceCourierRequestId);

    Optional<PartnerReferral> findBySourceRestaurantRequestId(UUID sourceRestaurantRequestId);

    List<PartnerReferral> findByCourierServiceIdOrderByCreatedAtDesc(UUID courierServiceId);
}
