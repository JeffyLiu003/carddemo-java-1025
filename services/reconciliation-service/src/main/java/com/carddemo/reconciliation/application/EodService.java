package com.carddemo.reconciliation.application;

import com.carddemo.reconciliation.domain.exception.NotFoundException;
import com.carddemo.reconciliation.domain.exception.RemoteServiceException;
import com.carddemo.reconciliation.domain.model.EodRun;
import com.carddemo.reconciliation.domain.repository.EodRunRepository;
import com.carddemo.reconciliation.domain.repository.PageResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * EOD 作业用例（A1.4）：当前作业图只有"对账"一步，成功回写 reconciliation_run_id，
 * 失败置 FAILED 并把异常抛给接口层（设计 §5：接口 503，EOD 历史可查失败原因）。
 */
@Service
public class EodService {

    private static final Logger log = LoggerFactory.getLogger(EodService.class);
    private static final int DEFAULT_PAGE_SIZE = 20;

    private final EodRunRepository eodRuns;
    private final ReconciliationService reconciliations;

    public EodService(EodRunRepository eodRuns, ReconciliationService reconciliations) {
        this.eodRuns = eodRuns;
        this.reconciliations = reconciliations;
    }

    public EodRun start(LocalDate businessDate) {
        Objects.requireNonNull(businessDate, "businessDate must not be null");
        EodRun run = eodRuns.save(EodRun.start(
                UUID.randomUUID().toString(), businessDate, LocalDateTime.now()));
        try {
            ReconciliationRunView reconciliation = reconciliations.run(businessDate);
            return eodRuns.save(run.complete(reconciliation.run().getRunId(),
                    messageOf(reconciliation), LocalDateTime.now()));
        } catch (RemoteServiceException ex) {
            log.warn("EOD for {} failed: {}", businessDate, ex.getMessage());
            eodRuns.save(run.fail(ex.getMessage(), LocalDateTime.now()));
            throw ex;
        }
    }

    @Transactional(readOnly = true)
    public EodRun get(String runId) {
        return eodRuns.findById(runId)
                .orElseThrow(() -> new NotFoundException("EOD_RUN_NOT_FOUND",
                        "EOD run " + runId + " not found"));
    }

    @Transactional(readOnly = true)
    public PageResult<EodRun> list(int page, int size) {
        return eodRuns.search(Math.max(page, 0), size <= 0 ? DEFAULT_PAGE_SIZE : size);
    }

    private static String messageOf(ReconciliationRunView reconciliation) {
        return reconciliation.run().isMatched()
                ? "Reconciliation matched"
                : "Reconciliation finished with " + reconciliation.run().getMismatchCount() + " differences";
    }
}
