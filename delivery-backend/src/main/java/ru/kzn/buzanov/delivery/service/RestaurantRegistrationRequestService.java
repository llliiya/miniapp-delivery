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
import ru.kzn.buzanov.delivery.domain.RestaurantRegistrationRequest;
import ru.kzn.buzanov.delivery.domain.RestaurantRegistrationRequestStatus;
import ru.kzn.buzanov.delivery.domain.RestaurantRegistrationSourceType;
import ru.kzn.buzanov.delivery.dto.ApproveRestaurantRegistrationResponse;
import ru.kzn.buzanov.delivery.dto.CreateRestaurantResponse;
import ru.kzn.buzanov.delivery.dto.ProvisioningCredentialsDto;
import ru.kzn.buzanov.delivery.dto.RestaurantRegistrationRequestDto;
import ru.kzn.buzanov.delivery.dto.request.CreateRestaurantRegistrationRequest;
import ru.kzn.buzanov.delivery.dto.request.CreateRestaurantRequest;
import ru.kzn.buzanov.delivery.dto.request.RestaurantOwnerRequest;
import ru.kzn.buzanov.delivery.repository.CourierProfileRepository;
import ru.kzn.buzanov.delivery.repository.OrganizationMemberRepository;
import ru.kzn.buzanov.delivery.repository.RestaurantRegistrationRequestRepository;
import ru.kzn.buzanov.delivery.util.EmailRequirements;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RestaurantRegistrationRequestService {

    private static final List<RestaurantRegistrationRequestStatus> PENDING_STATUSES = List.of(
            RestaurantRegistrationRequestStatus.NEW,
            RestaurantRegistrationRequestStatus.IN_PROGRESS);

    private final RestaurantRegistrationRequestRepository requestRepository;
    private final CourierProfileRepository courierProfileRepository;
    private final OrganizationMemberRepository memberRepository;
    private final AccessControlService accessControl;
    private final RestaurantService restaurantService;

    @Transactional
    public RestaurantRegistrationRequestDto createPublic(CreateRestaurantRegistrationRequest request) {
        String restaurantName = request.restaurantName().trim();
        String address = request.address().trim();
        String contactPerson = request.contactPerson().trim();
        String phone = normalizePhone(request.phone());
        String email = EmailRequirements.requireEmail(request.email());
        String comment = trimToNull(request.comment());

        if (restaurantName.isEmpty() || address.isEmpty() || contactPerson.isEmpty() || phone.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Заполните название, адрес, контактное лицо и телефон");
        }

        if (requestRepository.findFirstByPhoneAndStatusInOrderByCreatedAtDesc(phone, PENDING_STATUSES).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Заявка с этим телефоном уже на рассмотрении");
        }

        String partnerCodeRaw = trimToNull(request.partnerCode());
        UUID courierMemberId = null;
        String partnerCode = null;
        RestaurantRegistrationSourceType sourceType = RestaurantRegistrationSourceType.SELF;

        if (partnerCodeRaw != null) {
            String normalizedCode = partnerCodeRaw.trim().toUpperCase();
            CourierProfile profile = courierProfileRepository.findByPartnerCode(normalizedCode)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Недействительная партнёрская ссылка"));
            OrganizationMember courierMember = memberRepository.findById(profile.getMemberId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Недействительная партнёрская ссылка"));
            if (courierMember.getRole() != MemberRole.courier) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Недействительная партнёрская ссылка");
            }
            Organization courierService = accessControl.requireOrganization(courierMember.getOrganizationId());
            if (courierService.getType() != OrganizationType.courier_service) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Недействительная партнёрская ссылка");
            }
            courierMemberId = courierMember.getId();
            partnerCode = normalizedCode;
            sourceType = RestaurantRegistrationSourceType.PARTNER;
        }

        Instant now = Instant.now();
        RestaurantRegistrationRequest entity = new RestaurantRegistrationRequest();
        entity.setId(UUID.randomUUID());
        entity.setRestaurantName(restaurantName);
        entity.setAddress(address);
        entity.setContactPerson(contactPerson);
        entity.setPhone(phone);
        entity.setEmail(email);
        entity.setComment(comment);
        entity.setSourceType(sourceType);
        entity.setPartnerCode(partnerCode);
        entity.setCourierMemberId(courierMemberId);
        entity.setStatus(RestaurantRegistrationRequestStatus.NEW);
        entity.setCreatedAt(now);
        requestRepository.save(entity);
        return toDto(entity);
    }

    @Transactional(readOnly = true)
    public List<RestaurantRegistrationRequestDto> listForService(Long userId, UUID courierServiceId) {
        accessControl.requireServiceStaff(userId, courierServiceId);
        return requestRepository
                .findVisibleForCourierService(
                        courierServiceId,
                        RestaurantRegistrationSourceType.SELF,
                        RestaurantRegistrationSourceType.ADMIN)
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public RestaurantRegistrationRequestDto get(Long userId, UUID courierServiceId, UUID requestId) {
        accessControl.requireServiceStaff(userId, courierServiceId);
        RestaurantRegistrationRequest entity = requireVisibleRequest(requestId, courierServiceId);
        return toDto(entity);
    }

    @Transactional
    public RestaurantRegistrationRequestDto markInProgress(Long userId, UUID courierServiceId, UUID requestId) {
        accessControl.requireServiceStaff(userId, courierServiceId);
        RestaurantRegistrationRequest entity = requireVisibleRequest(requestId, courierServiceId);
        if (entity.getStatus() == RestaurantRegistrationRequestStatus.NEW) {
            entity.setStatus(RestaurantRegistrationRequestStatus.IN_PROGRESS);
            requestRepository.save(entity);
        }
        return toDto(entity);
    }

    @Transactional
    public ApproveRestaurantRegistrationResponse approve(Long userId, UUID courierServiceId, UUID requestId) {
        accessControl.requireServiceStaff(userId, courierServiceId);
        RestaurantRegistrationRequest entity = requireVisibleRequest(requestId, courierServiceId);
        if (!PENDING_STATUSES.contains(entity.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Заявка уже обработана");
        }
        assertPartnerBelongsToService(entity, courierServiceId);

        CreateRestaurantRequest createRequest = new CreateRestaurantRequest(
                entity.getRestaurantName(),
                courierServiceId,
                new RestaurantOwnerRequest(
                        entity.getContactPerson(),
                        entity.getPhone(),
                        entity.getEmail()));
        CreateRestaurantResponse created = restaurantService.createForRegistrationApproval(userId, createRequest);

        Instant now = Instant.now();
        entity.setStatus(RestaurantRegistrationRequestStatus.APPROVED);
        entity.setRestaurantId(created.object().id());
        entity.setProcessedAt(now);
        entity.setProcessedBy(userId);
        requestRepository.save(entity);

        ProvisioningCredentialsDto credentials = created.ownerCredentials();
        return new ApproveRestaurantRegistrationResponse(
                entity.getId(),
                entity.getStatus(),
                created.object().id(),
                credentials,
                credentials != null
                        ? "Объект создан. Передайте логин и пароль владельцу."
                        : "Объект создан.");
    }

    @Transactional
    public RestaurantRegistrationRequestDto reject(Long userId, UUID courierServiceId, UUID requestId) {
        accessControl.requireServiceStaff(userId, courierServiceId);
        RestaurantRegistrationRequest entity = requireVisibleRequest(requestId, courierServiceId);
        if (!PENDING_STATUSES.contains(entity.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Заявка уже обработана");
        }
        assertPartnerBelongsToService(entity, courierServiceId);
        entity.setStatus(RestaurantRegistrationRequestStatus.REJECTED);
        entity.setProcessedAt(Instant.now());
        entity.setProcessedBy(userId);
        return toDto(requestRepository.save(entity));
    }

    private RestaurantRegistrationRequest requireVisibleRequest(UUID requestId, UUID courierServiceId) {
        RestaurantRegistrationRequest entity = requestRepository.findById(requestId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Заявка не найдена"));
        if (entity.getSourceType() == RestaurantRegistrationSourceType.SELF) {
            return entity;
        }
        if (entity.getSourceType() == RestaurantRegistrationSourceType.ADMIN && entity.getRestaurantId() != null) {
            Organization restaurant = accessControl.requireOrganization(entity.getRestaurantId());
            if (courierServiceId.equals(restaurant.getCourierServiceId())) {
                return entity;
            }
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Заявка не найдена");
        }
        if (entity.getCourierMemberId() == null) {
            return entity;
        }
        OrganizationMember courierMember = memberRepository.findById(entity.getCourierMemberId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Заявка не найдена"));
        if (!courierServiceId.equals(courierMember.getOrganizationId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Заявка не найдена");
        }
        return entity;
    }

    private void assertPartnerBelongsToService(RestaurantRegistrationRequest entity, UUID courierServiceId) {
        if (entity.getSourceType() != RestaurantRegistrationSourceType.PARTNER) {
            return;
        }
        if (entity.getCourierMemberId() == null) {
            return;
        }
        OrganizationMember courierMember = memberRepository.findById(entity.getCourierMemberId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Курьер не найден"));
        if (!courierServiceId.equals(courierMember.getOrganizationId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Заявка относится к другой службе");
        }
    }

    private RestaurantRegistrationRequestDto toDto(RestaurantRegistrationRequest entity) {
        String courierName = null;
        if (entity.getCourierMemberId() != null) {
            courierName = memberRepository.findById(entity.getCourierMemberId())
                    .map(m -> m.getDisplayName() != null ? m.getDisplayName() : "Курьер")
                    .orElse(null);
        }
        return new RestaurantRegistrationRequestDto(
                entity.getId(),
                entity.getRestaurantName(),
                entity.getAddress(),
                entity.getContactPerson(),
                entity.getPhone(),
                entity.getEmail(),
                entity.getComment(),
                entity.getSourceType(),
                formatSourceLabel(entity.getSourceType(), courierName),
                entity.getPartnerCode(),
                entity.getCourierMemberId(),
                courierName,
                entity.getRestaurantId(),
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getProcessedAt(),
                entity.getProcessedBy());
    }

    static String formatSourceLabel(RestaurantRegistrationSourceType sourceType, String courierName) {
        return switch (sourceType) {
            case SELF -> "Самостоятельная регистрация";
            case PARTNER -> courierName != null && !courierName.isBlank()
                    ? "Приглашен курьером " + courierName
                    : "Приглашен курьером";
            case ADMIN -> "Добавлен администратором";
        };
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
}
