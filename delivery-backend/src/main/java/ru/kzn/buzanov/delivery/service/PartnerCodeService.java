package ru.kzn.buzanov.delivery.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.kzn.buzanov.delivery.domain.CourierProfile;
import ru.kzn.buzanov.delivery.domain.MemberRole;
import ru.kzn.buzanov.delivery.domain.Organization;
import ru.kzn.buzanov.delivery.domain.OrganizationMember;
import ru.kzn.buzanov.delivery.domain.OrganizationType;
import ru.kzn.buzanov.delivery.domain.PartnerReferrerType;
import ru.kzn.buzanov.delivery.dto.PartnerReferrer;
import ru.kzn.buzanov.delivery.repository.CourierProfileRepository;
import ru.kzn.buzanov.delivery.repository.OrganizationMemberRepository;
import ru.kzn.buzanov.delivery.repository.OrganizationRepository;
import ru.kzn.buzanov.delivery.util.PartnerCodeGenerator;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PartnerCodeService {

    private final CourierProfileRepository courierProfileRepository;
    private final OrganizationRepository organizationRepository;
    private final OrganizationMemberRepository memberRepository;
    private final AccessControlService accessControl;

    @Transactional
    public String ensurePartnerCodeForCourier(UUID memberId) {
        CourierProfile profile = courierProfileRepository.findByMemberId(memberId)
                .orElseThrow(() -> new IllegalStateException("Courier profile not found: " + memberId));
        if (profile.getPartnerCode() != null && !profile.getPartnerCode().isBlank()) {
            return profile.getPartnerCode();
        }
        String code = generateUniqueCode();
        profile.setPartnerCode(code);
        profile.setUpdatedAt(Instant.now());
        courierProfileRepository.save(profile);
        return code;
    }

    @Transactional
    public String ensurePartnerCodeForRestaurant(UUID organizationId) {
        Organization organization = accessControl.requireRestaurant(organizationId);
        if (organization.getPartnerCode() != null && !organization.getPartnerCode().isBlank()) {
            return organization.getPartnerCode();
        }
        String code = generateUniqueCode();
        organization.setPartnerCode(code);
        organizationRepository.save(organization);
        return code;
    }

    @Transactional(readOnly = true)
    public PartnerReferrer resolvePartnerCode(String rawCode) {
        if (rawCode == null || rawCode.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Недействительная партнёрская ссылка");
        }
        String normalizedCode = rawCode.trim().toUpperCase();

        var courierProfile = courierProfileRepository.findByPartnerCode(normalizedCode);
        if (courierProfile.isPresent()) {
            return toCourierReferrer(courierProfile.get(), normalizedCode);
        }

        var restaurant = organizationRepository.findByPartnerCode(normalizedCode);
        if (restaurant.isPresent()) {
            return toRestaurantReferrer(restaurant.get(), normalizedCode);
        }

        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Недействительная партнёрская ссылка");
    }

    private PartnerReferrer toCourierReferrer(CourierProfile profile, String code) {
        OrganizationMember courierMember = memberRepository.findById(profile.getMemberId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Недействительная партнёрская ссылка"));
        if (courierMember.getRole() != MemberRole.courier) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Недействительная партнёрская ссылка");
        }
        Organization courierService = accessControl.requireOrganization(courierMember.getOrganizationId());
        if (courierService.getType() != OrganizationType.courier_service) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Недействительная партнёрская ссылка");
        }
        String displayName = courierMember.getDisplayName() != null && !courierMember.getDisplayName().isBlank()
                ? courierMember.getDisplayName().trim()
                : "Курьер";
        return new PartnerReferrer(
                PartnerReferrerType.COURIER,
                code,
                courierMember.getId(),
                courierService.getId(),
                courierService.getId(),
                displayName);
    }

    private PartnerReferrer toRestaurantReferrer(Organization organization, String code) {
        if (organization.getType() != OrganizationType.client_restaurant) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Недействительная партнёрская ссылка");
        }
        if (organization.getCourierServiceId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Недействительная партнёрская ссылка");
        }
        String displayName = organization.getName() != null && !organization.getName().isBlank()
                ? organization.getName().trim()
                : "Объект";
        return new PartnerReferrer(
                PartnerReferrerType.RESTAURANT,
                code,
                null,
                organization.getId(),
                organization.getCourierServiceId(),
                displayName);
    }

    private String generateUniqueCode() {
        for (int attempt = 0; attempt < 20; attempt++) {
            String code = PartnerCodeGenerator.generate();
            if (!codeExists(code)) {
                return code;
            }
        }
        throw new IllegalStateException("Не удалось сгенерировать уникальный partner code");
    }

    private boolean codeExists(String code) {
        return courierProfileRepository.existsByPartnerCode(code)
                || organizationRepository.existsByPartnerCode(code);
    }
}
