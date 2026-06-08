package ru.kzn.buzanov.delivery.service;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.kzn.buzanov.delivery.domain.DeliveryAccountStatus;
import ru.kzn.buzanov.delivery.domain.MemberRole;
import ru.kzn.buzanov.delivery.domain.MemberStatus;
import ru.kzn.buzanov.delivery.domain.OrganizationMember;
import ru.kzn.buzanov.delivery.domain.UserDeliveryContext;
import ru.kzn.buzanov.delivery.repository.UserDeliveryContextRepository;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class DeliveryUserProfileService {

    private final UserDeliveryContextRepository contextRepository;

    @Transactional
    public UserDeliveryContext ensureProfile(Long userId) {
        return contextRepository.findById(userId).orElseGet(() -> createDefault(userId));
    }

    @Transactional
    public void syncFromMembership(OrganizationMember member) {
        if (member == null) {
            return;
        }
        UserDeliveryContext ctx = ensureProfile(member.getUserId());
        ctx.setDeliveryRole(member.getRole());
        ctx.setAccountStatus(mapMemberStatus(member.getStatus()));
        ctx.setUpdatedAt(Instant.now());
        contextRepository.save(ctx);
    }

    @Transactional
    public void activateAs(Long userId, MemberRole role) {
        UserDeliveryContext ctx = ensureProfile(userId);
        ctx.setDeliveryRole(role);
        ctx.setAccountStatus(DeliveryAccountStatus.active);
        ctx.setUpdatedAt(Instant.now());
        contextRepository.save(ctx);
    }

    public void requireActiveAccount(Long userId) {
        UserDeliveryContext ctx = contextRepository.findById(userId).orElse(null);
        if (ctx == null || ctx.getAccountStatus() != DeliveryAccountStatus.active) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Аккаунт delivery не активирован");
        }
    }

    public boolean isPendingCourier(UserDeliveryContext ctx) {
        return ctx != null
                && ctx.getDeliveryRole() == MemberRole.courier
                && ctx.getAccountStatus() == DeliveryAccountStatus.inactive;
    }

    private UserDeliveryContext createDefault(Long userId) {
        Instant now = Instant.now();
        UserDeliveryContext created = new UserDeliveryContext();
        created.setUserId(userId);
        created.setDeliveryRole(MemberRole.courier);
        created.setAccountStatus(DeliveryAccountStatus.inactive);
        created.setCreatedAt(now);
        created.setUpdatedAt(now);
        return contextRepository.save(created);
    }

    private static DeliveryAccountStatus mapMemberStatus(MemberStatus status) {
        if (status == MemberStatus.blocked) {
            return DeliveryAccountStatus.blocked;
        }
        if (status == MemberStatus.active) {
            return DeliveryAccountStatus.active;
        }
        return DeliveryAccountStatus.inactive;
    }
}
