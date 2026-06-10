package ru.kzn.buzanov.delivery.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.kzn.buzanov.delivery.domain.CourierProfile;
import ru.kzn.buzanov.delivery.repository.CourierProfileRepository;
import ru.kzn.buzanov.delivery.util.PartnerCodeGenerator;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PartnerCodeService {

    private final CourierProfileRepository courierProfileRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String ensurePartnerCode(UUID memberId) {
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

    private String generateUniqueCode() {
        for (int attempt = 0; attempt < 20; attempt++) {
            String code = PartnerCodeGenerator.generate();
            if (!courierProfileRepository.existsByPartnerCode(code)) {
                return code;
            }
        }
        throw new IllegalStateException("Не удалось сгенерировать уникальный partner code");
    }
}
