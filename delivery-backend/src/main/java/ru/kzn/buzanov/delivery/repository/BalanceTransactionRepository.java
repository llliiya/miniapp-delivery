package ru.kzn.buzanov.delivery.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.kzn.buzanov.delivery.domain.BalanceTransaction;
import ru.kzn.buzanov.delivery.domain.BalanceTransactionType;

import java.util.UUID;

public interface BalanceTransactionRepository extends JpaRepository<BalanceTransaction, UUID> {

    boolean existsByOrderIdAndType(UUID orderId, BalanceTransactionType type);
}
