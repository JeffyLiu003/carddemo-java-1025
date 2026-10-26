package com.carddemo.dispute.application;

import com.carddemo.dispute.domain.exception.InvalidRequestException;
import com.carddemo.dispute.domain.exception.NotFoundException;
import com.carddemo.dispute.domain.model.DisputeCase;
import com.carddemo.dispute.domain.model.DisputeStatus;
import com.carddemo.dispute.domain.repository.DisputeCaseFilter;
import com.carddemo.dispute.domain.repository.DisputeCaseRepository;
import com.carddemo.dispute.domain.repository.PageResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 争议案件用例（设计 §5.2）：开案、查询、受理审核、临时入账、chargeback、结案。
 */
@Service
@Transactional
public class DisputeCaseService {

    private static final int DEFAULT_PAGE_SIZE = 10;

    private final DisputeCaseRepository disputes;

    public DisputeCaseService(DisputeCaseRepository disputes) {
        this.disputes = disputes;
    }

    public DisputeCase open(OpenDisputeCommand command) {
        DisputeCase disputeCase = DisputeCase.open(UUID.randomUUID().toString(), command.transactionId(),
                command.accountId(), command.customerId(), command.reasonCode(), command.amount(),
                LocalDateTime.now());
        return disputes.saveCase(disputeCase);
    }

    @Transactional(readOnly = true)
    public DisputeCase get(String caseId) {
        return requireCase(caseId);
    }

    @Transactional(readOnly = true)
    public PageResult<DisputeCase> list(String status, String accountId, int page, int size) {
        DisputeCaseFilter filter = new DisputeCaseFilter(parseStatusFilter(status), trimToNull(accountId));
        return disputes.search(filter, Math.max(page, 0), size <= 0 ? DEFAULT_PAGE_SIZE : size);
    }

    public DisputeCase review(String caseId) {
        DisputeCase disputeCase = requireCase(caseId);
        disputeCase.review(LocalDateTime.now());
        return disputes.saveCase(disputeCase);
    }

    /**
     * 临时入账：金额必须大于 0 且不超过争议金额，否则 409。
     */
    public DisputeCase grantProvisionalCredit(String caseId, BigDecimal amount) {
        DisputeCase disputeCase = requireCase(caseId);
        disputeCase.grantProvisionalCredit(amount, LocalDateTime.now());
        return disputes.saveCase(disputeCase);
    }

    public DisputeCase chargeback(String caseId) {
        DisputeCase disputeCase = requireCase(caseId);
        disputeCase.chargeback(LocalDateTime.now());
        return disputes.saveCase(disputeCase);
    }

    public DisputeCase resolve(String caseId, boolean won) {
        DisputeCase disputeCase = requireCase(caseId);
        disputeCase.resolve(won, LocalDateTime.now());
        return disputes.saveCase(disputeCase);
    }

    private DisputeCase requireCase(String caseId) {
        return disputes.findByCaseId(caseId)
                .orElseThrow(() -> new NotFoundException("DISPUTE_NOT_FOUND",
                        "Dispute case " + caseId + " not found"));
    }

    private static DisputeStatus parseStatusFilter(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        for (DisputeStatus candidate : DisputeStatus.values()) {
            if (candidate.name().equalsIgnoreCase(status.trim())) {
                return candidate;
            }
        }
        throw new InvalidRequestException("INVALID_DISPUTE_STATUS",
                "Status filter must be one of OPEN, UNDER_REVIEW, PROVISIONAL_CREDIT, CHARGEBACK,"
                        + " WON, LOST, CLOSED.");
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
