package ru.kzn.buzanov.delivery.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.kzn.buzanov.delivery.domain.PartnerAccount;
import ru.kzn.buzanov.delivery.domain.PartnerPayoutRequest;
import ru.kzn.buzanov.delivery.domain.PartnerPayoutStatus;
import ru.kzn.buzanov.delivery.repository.PartnerPayoutRequestRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PartnerPayoutExecutionService {

    private final PartnerPayoutRequestRepository payoutRepository;
    private final PartnerAccountService accountService;
    private final PartnerBalanceTransferService balanceTransferService;

    @Scheduled(cron = "0 5 0 * * *", zone = "UTC")
    @Transactional
    public void processDuePayoutsScheduled() {
        processDuePayouts(LocalDate.now(ZoneOffset.UTC));
    }

    @Transactional
    public void processDuePayouts(LocalDate payoutDate) {
        balanceTransferService.processDueTransfers(payoutDate);
        List<PartnerPayoutRequest> duePayouts =
                payoutRepository.findByStatusAndScheduledPayoutDateLessThanEqual(
                        PartnerPayoutStatus.SCHEDULED, payoutDate);
        Instant now = Instant.now();
        for (PartnerPayoutRequest payout : duePayouts) {
            processDuePayout(payout, now);
        }
    }

    private void processDuePayout(PartnerPayoutRequest payout, Instant now) {
        PartnerAccount account = accountService.requireById(payout.getPartnerAccountId());
        payout.setStatus(PartnerPayoutStatus.PENDING);
        payout.setUpdatedAt(now);
        payoutRepository.save(payout);
        accountService.syncAccountFromSources(account.getId());
    }
}
