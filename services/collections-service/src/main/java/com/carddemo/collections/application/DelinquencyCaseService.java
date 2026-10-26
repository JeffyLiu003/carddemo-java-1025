package com.carddemo.collections.application;

import com.carddemo.collections.domain.exception.InvalidRequestException;
import com.carddemo.collections.domain.exception.NotFoundException;
import com.carddemo.collections.domain.model.CaseStatus;
import com.carddemo.collections.domain.model.DelinquencyBucket;
import com.carddemo.collections.domain.model.DelinquencyCase;
import com.carddemo.collections.domain.repository.DelinquencyCaseFilter;
import com.carddemo.collections.domain.repository.DelinquencyCaseRepository;
import com.carddemo.collections.domain.repository.PageResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;

/**
 * 催收用例（A1.5）：建案分桶、重算、登记承诺还款、结案。
 */
@Service
public class DelinquencyCaseService {

    private static final int DEFAULT_PAGE_SIZE = 20;

    private final DelinquencyCaseRepository cases;

    public DelinquencyCaseService(DelinquencyCaseRepository cases) {
        this.cases = cases;
    }

    @Transactional
    public DelinquencyCase open(OpenDelinquencyCaseCommand command) {
        return cases.save(DelinquencyCase.open(UUID.randomUUID().toString(), command.accountId(),
                command.daysPastDue(), command.outstandingAmount(), LocalDateTime.now()));
    }

    @Transactional(readOnly = true)
    public DelinquencyCase get(String caseId) {
        return requireCase(caseId);
    }

    @Transactional(readOnly = true)
    public PageResult<DelinquencyCase> list(String status, String bucket, int page, int size) {
        DelinquencyCaseFilter filter = new DelinquencyCaseFilter(parseStatus(status), parseBucket(bucket));
        return cases.search(filter, Math.max(page, 0), size <= 0 ? DEFAULT_PAGE_SIZE : size);
    }

    @Transactional
    public DelinquencyCase recalculate(String caseId, int daysPastDue) {
        DelinquencyCase delinquencyCase = requireCase(caseId);
        return cases.save(delinquencyCase.recalculate(daysPastDue, LocalDateTime.now(), LocalDate.now()));
    }

    @Transactional
    public DelinquencyCase promiseToPay(String caseId, LocalDate promiseDate) {
        DelinquencyCase delinquencyCase = requireCase(caseId);
        return cases.save(delinquencyCase.promiseToPay(promiseDate, LocalDateTime.now(), LocalDate.now()));
    }

    @Transactional
    public DelinquencyCase resolve(String caseId) {
        DelinquencyCase delinquencyCase = requireCase(caseId);
        return cases.save(delinquencyCase.resolve(LocalDateTime.now()));
    }

    private DelinquencyCase requireCase(String caseId) {
        return cases.findById(caseId)
                .orElseThrow(() -> new NotFoundException("DELINQUENCY_CASE_NOT_FOUND",
                        "Delinquency case " + caseId + " not found"));
    }

    private static CaseStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return CaseStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new InvalidRequestException("INVALID_STATUS",
                    "Status filter must be one of OPEN, PTP, RESOLVED.");
        }
    }

    private static DelinquencyBucket parseBucket(String bucket) {
        if (bucket == null || bucket.isBlank()) {
            return null;
        }
        try {
            return DelinquencyBucket.valueOf(bucket.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new InvalidRequestException("INVALID_BUCKET",
                    "Bucket filter must be one of B1, B2, B3, B4.");
        }
    }
}
