package ru.kzn.buzanov.delivery.repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public interface PartnerReferralAccrualStats {

    UUID getPartnerReferralId();

    long getAccrualCount();

    BigDecimal getAccruedTotal();

    BigDecimal getReversedTotal();

    Instant getLastAccrualAt();
}
