package ru.kzn.buzanov.delivery.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.kzn.buzanov.delivery.domain.PartnerLedgerTransaction;

import java.util.UUID;

public interface PartnerLedgerTransactionRepository extends JpaRepository<PartnerLedgerTransaction, UUID> {
}
