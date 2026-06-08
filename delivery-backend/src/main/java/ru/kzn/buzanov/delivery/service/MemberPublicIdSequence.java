package ru.kzn.buzanov.delivery.service;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MemberPublicIdSequence {

    private static final String SEQUENCE = "delivery.organization_members_public_id_seq";

    private final EntityManager entityManager;

    public long next() {
        Object value = entityManager.createNativeQuery("SELECT nextval('" + SEQUENCE + "')").getSingleResult();
        return ((Number) value).longValue();
    }
}
