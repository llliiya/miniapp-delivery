package ru.kzn.buzanov.delivery.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.kzn.buzanov.delivery.domain.CourierRequest;
import ru.kzn.buzanov.delivery.domain.CourierRequestStatus;
import ru.kzn.buzanov.delivery.domain.MemberRole;
import ru.kzn.buzanov.delivery.domain.MemberStatus;
import ru.kzn.buzanov.delivery.domain.OrganizationMember;
import ru.kzn.buzanov.delivery.domain.OrganizationType;
import ru.kzn.buzanov.delivery.domain.PartnerReferrerType;
import ru.kzn.buzanov.delivery.dto.ApproveCourierRequestResponse;
import ru.kzn.buzanov.delivery.dto.CourierRequestDto;
import ru.kzn.buzanov.delivery.dto.MessengerRegistrationStatusDto;
import ru.kzn.buzanov.delivery.dto.PartnerReferrer;
import ru.kzn.buzanov.delivery.dto.request.CreateCourierRequestRequest;
import ru.kzn.buzanov.delivery.integration.AccountProvisioningClient;
import ru.kzn.buzanov.delivery.integration.AccountUserClient;
import ru.kzn.buzanov.delivery.integration.account.AccountProvisionRequest;
import ru.kzn.buzanov.delivery.integration.account.AccountProvisionResult;
import ru.kzn.buzanov.delivery.repository.CourierRequestRepository;
import ru.kzn.buzanov.delivery.repository.OrganizationMemberRepository;
import ru.kzn.buzanov.delivery.service.notification.CourierRequestNotificationService;
import ru.kzn.buzanov.delivery.util.CityNormalizer;
import ru.kzn.buzanov.delivery.util.EmailRequirements;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CourierRequestService {

    private final CourierRequestRepository requestRepository;
    private final OrganizationMemberRepository memberRepository;
    private final AccountUserClient accountUserClient;
    private final AccountProvisioningClient accountProvisioningClient;
    private final MemberService memberService;
    private final DeliveryUserProfileService profileService;
    private final AccessControlService accessControl;
    private final CourierRequestNotificationService notificationService;
    private final PartnerCodeService partnerCodeService;

    @Transactional(readOnly = true)
    public MessengerRegistrationStatusDto messengerStatus(String provider, String externalId) {
        String normalizedProvider = normalizeProvider(provider);
        String normalizedExternalId = normalizeExternalId(externalId);
        if (normalizedProvider.isEmpty() || normalizedExternalId.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Укажите messenger provider и external id");
        }

        boolean registered = accountUserClient
                .findUserIdByExternalIdentity(normalizedProvider, normalizedExternalId)
                .map(this::hasActiveCourierMembership)
                .orElse(false);

        Optional<CourierRequest> pending = requestRepository
                .findFirstByMessengerProviderAndMessengerExternalIdAndStatusOrderByCreatedAtDesc(
                        normalizedProvider, normalizedExternalId, CourierRequestStatus.NEW);

        Optional<CourierRequest> rejected = requestRepository
                .findFirstByMessengerProviderAndMessengerExternalIdAndStatusOrderByCreatedAtDesc(
                        normalizedProvider, normalizedExternalId, CourierRequestStatus.REJECTED);

        return new MessengerRegistrationStatusDto(
                registered,
                pending.isPresent(),
                rejected.isPresent() && pending.isEmpty() && !registered);
    }

    @Transactional
    public CourierRequestDto create(CreateCourierRequestRequest request) {
        String fullName = request.fullName().trim();
        String phone = normalizePhone(request.phone());
        String email = EmailRequirements.requireEmail(request.email());
        String city = request.city().trim();
        String comment = trimToNull(request.comment());
        String provider = normalizeProvider(request.messengerProvider());
        String externalId = normalizeExternalId(request.messengerExternalId());
        boolean hasMessenger = !provider.isEmpty() && !externalId.isEmpty();

        if (fullName.isEmpty() || phone.isEmpty() || city.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Заполните ФИО, телефон и город");
        }

        if (hasMessenger) {
            MessengerRegistrationStatusDto status = messengerStatus(provider, externalId);
            if (status.registered()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "messenger_already_registered");
            }
            if (status.applicationPending()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "application_already_pending");
            }
        } else if (requestRepository.findFirstByPhoneAndStatusOrderByCreatedAtDesc(phone, CourierRequestStatus.NEW).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Заявка с этим телефоном уже на рассмотрении");
        }

        String partnerCodeRaw = trimToNull(request.partnerCode());
        UUID referrerMemberId = null;
        UUID referrerOrganizationId = null;
        String partnerCode = null;
        if (partnerCodeRaw != null) {
            PartnerReferrer referrer = partnerCodeService.resolvePartnerCode(partnerCodeRaw);
            partnerCode = referrer.partnerCode();
            if (referrer.type() == PartnerReferrerType.COURIER) {
                referrerMemberId = referrer.memberId();
            } else {
                referrerOrganizationId = referrer.organizationId();
            }
        }

        Instant now = Instant.now();
        CourierRequest entity = new CourierRequest();
        entity.setId(UUID.randomUUID());
        entity.setFullName(fullName);
        entity.setPhone(phone);
        entity.setEmail(email);
        entity.setCity(CityNormalizer.normalize(city));
        entity.setComment(comment);
        entity.setTransport(request.transport() != null && !request.transport().isBlank()
                ? request.transport().trim()
                : null);
        entity.setMessengerProvider(hasMessenger ? provider : null);
        entity.setMessengerExternalId(hasMessenger ? externalId : null);
        entity.setMessengerUsername(trimToNull(request.messengerUsername()));
        entity.setSource(hasMessenger ? "messenger" : partnerCode != null ? "partner" : "web");
        entity.setPartnerCode(partnerCode);
        entity.setReferrerMemberId(referrerMemberId);
        entity.setReferrerOrganizationId(referrerOrganizationId);
        entity.setStatus(CourierRequestStatus.NEW);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        requestRepository.save(entity);
        notificationService.notifyAdminNewRequest(entity);
        return toDto(entity);
    }

    @Transactional(readOnly = true)
    public List<CourierRequestDto> listPending(Long userId, UUID courierServiceId, String city) {
        accessControl.requireServiceStaff(userId, courierServiceId);
        String cityFilter = CityNormalizer.normalize(city);
        return requestRepository.findByStatusOrderByCreatedAtDesc(CourierRequestStatus.NEW).stream()
                .filter(request -> cityFilter == null || CityNormalizer.equals(request.getCity(), cityFilter))
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public ApproveCourierRequestResponse approve(Long userId, UUID courierServiceId, UUID requestId) {
        accessControl.requireServiceStaff(userId, courierServiceId);
        CourierRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Заявка не найдена"));
        if (request.getStatus() != CourierRequestStatus.NEW) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Заявка уже обработана");
        }
        String requestEmail = request.getEmail();
        if (requestEmail == null || requestEmail.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "В заявке не указан email. Попросите курьера подать заявку заново.");
        }

        boolean hasMessenger = request.getMessengerProvider() != null
                && request.getMessengerExternalId() != null
                && !request.getMessengerProvider().isBlank()
                && !request.getMessengerExternalId().isBlank();

        Long courierUserId;

        if (hasMessenger) {
            Optional<Long> existingUserId = accountUserClient.findUserIdByExternalIdentity(
                    request.getMessengerProvider(), request.getMessengerExternalId());
            if (existingUserId.isPresent()) {
                courierUserId = existingUserId.get();
                assertNotAlreadyCourier(courierServiceId, courierUserId);
                try {
                    accountUserClient.linkMessengerIdentity(
                            courierUserId,
                            request.getMessengerProvider(),
                            request.getMessengerExternalId());
                } catch (ResponseStatusException ex) {
                    if (ex.getStatusCode() != HttpStatus.CONFLICT) {
                        throw ex;
                    }
                }
            } else {
                AccountProvisionResult provisioned = provisionCourierAccount(
                        request.getFullName(), request.getPhone(), requestEmail);
                courierUserId = provisioned.userId();
                accountUserClient.linkMessengerIdentity(
                        courierUserId,
                        request.getMessengerProvider(),
                        request.getMessengerExternalId());
            }
        } else {
            AccountProvisionResult provisioned = provisionCourierAccount(
                    request.getFullName(), request.getPhone(), requestEmail);
            courierUserId = provisioned.userId();
        }

        var member = memberService.addMembershipForOrganization(
                courierServiceId,
                courierUserId,
                MemberRole.courier,
                request.getFullName().trim());

        Instant now = Instant.now();
        request.setStatus(CourierRequestStatus.APPROVED);
        request.setLinkedUserId(courierUserId);
        request.setUpdatedAt(now);
        requestRepository.save(request);

        OrganizationMember organizationMember = memberRepository.findById(member.id()).orElseThrow();
        profileService.syncFromMembership(organizationMember);

        String message = hasMessenger
                ? "Курьер одобрен. Telegram/MAX привязан автоматически."
                : "Курьер одобрен. Логин и пароль отправлены на указанный телефон.";

        return new ApproveCourierRequestResponse(
                request.getId(),
                request.getStatus(),
                organizationMember.getId(),
                courierUserId,
                message);
    }

    @Transactional
    public CourierRequestDto reject(Long userId, UUID courierServiceId, UUID requestId) {
        accessControl.requireServiceStaff(userId, courierServiceId);
        CourierRequest request = requestRepository.findById(requestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Заявка не найдена"));
        if (request.getStatus() != CourierRequestStatus.NEW) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Заявка уже обработана");
        }
        request.setStatus(CourierRequestStatus.REJECTED);
        request.setUpdatedAt(Instant.now());
        return toDto(requestRepository.save(request));
    }

    private void assertNotAlreadyCourier(UUID courierServiceId, Long userId) {
        memberRepository.findByOrganizationIdAndUserId(courierServiceId, userId).ifPresent(member -> {
            if (member.getRole() == MemberRole.courier) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Курьер уже добавлен в службу");
            }
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Пользователь уже участник с другой ролью");
        });
    }

    private AccountProvisionResult provisionCourierAccount(String fullName, String phone, String email) {
        return accountProvisioningClient.provisionWebEmployee(
                AccountProvisionRequest.forCourier(fullName, phone, email));
    }

    private boolean hasActiveCourierMembership(Long userId) {
        List<OrganizationMember> members = memberRepository.findByUserIdAndStatus(userId, MemberStatus.active);
        for (OrganizationMember member : members) {
            if (member.getRole() != MemberRole.courier) {
                continue;
            }
            try {
                if (accessControl.requireOrganization(member.getOrganizationId()).getType()
                        == OrganizationType.courier_service) {
                    return true;
                }
            } catch (ResponseStatusException ignored) {
                // skip invalid org
            }
        }
        return false;
    }

    private static String normalizeProvider(String provider) {
        if (provider == null) {
            return "";
        }
        return provider.trim().toUpperCase();
    }

    private static String normalizeExternalId(String externalId) {
        if (externalId == null) {
            return "";
        }
        return externalId.trim();
    }

    private static String normalizePhone(String raw) {
        if (raw == null) {
            return "";
        }
        String digits = raw.replaceAll("\\D", "");
        if (digits.length() == 11 && digits.startsWith("8")) {
            digits = "7" + digits.substring(1);
        }
        if (digits.length() == 10) {
            digits = "7" + digits;
        }
        if (digits.length() == 11 && digits.startsWith("7")) {
            return "+" + digits;
        }
        return raw.trim();
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private CourierRequestDto toDto(CourierRequest entity) {
        return new CourierRequestDto(
                entity.getId(),
                entity.getFullName(),
                entity.getPhone(),
                entity.getEmail(),
                entity.getCity(),
                entity.getTransport(),
                entity.getComment(),
                entity.getMessengerProvider(),
                entity.getMessengerExternalId(),
                entity.getMessengerUsername(),
                entity.getSource(),
                entity.getStatus(),
                entity.getLinkedUserId(),
                entity.getCreatedAt());
    }
}
