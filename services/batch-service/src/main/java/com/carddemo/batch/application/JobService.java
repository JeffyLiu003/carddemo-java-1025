package com.carddemo.batch.application;

import com.carddemo.batch.domain.exception.BusinessRuleException;
import com.carddemo.batch.domain.exception.InvalidRequestException;
import com.carddemo.batch.domain.exception.NotFoundException;
import com.carddemo.batch.domain.exception.RemoteServiceException;
import com.carddemo.batch.domain.model.JobDefinition;
import com.carddemo.batch.domain.model.JobRun;
import com.carddemo.batch.domain.model.RunStatus;
import com.carddemo.batch.domain.repository.JobDefinitionRepository;
import com.carddemo.batch.domain.repository.JobRunFilter;
import com.carddemo.batch.domain.repository.JobRunRepository;
import com.carddemo.batch.domain.repository.PageResult;
import com.carddemo.batch.infrastructure.client.ReconciliationJobClient;
import com.carddemo.batch.infrastructure.client.TransactionJobClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * 批处理用例（A1.6）：按作业定义触发下游 EOD 作业，并把每次执行落成 job_run。
 *
 * <p>事务边界：execute 不加 {@code @Transactional}——下游调用不能和 RUNNING 记录同事务，
 * 否则下游失败会把 RUNNING 一起回滚掉。先落 RUNNING，成功后写 COMPLETED（message 含下游摘要），
 * 失败置 FAILED 保留原因再把 503 语义的异常抛给接口层。
 */
@Service
public class JobService {

    private static final Logger log = LoggerFactory.getLogger(JobService.class);

    private static final String TARGET_TRANSACTION = "transaction";
    private static final String TARGET_RECONCILIATION = "reconciliation";
    private static final Set<String> SUPPORTED_TARGETS = Set.of(TARGET_TRANSACTION, TARGET_RECONCILIATION);
    private static final int DEFAULT_PAGE_SIZE = 20;

    private final JobDefinitionRepository definitions;
    private final JobRunRepository runs;
    private final TransactionJobClient transactions;
    private final ReconciliationJobClient reconciliations;

    public JobService(JobDefinitionRepository definitions,
                      JobRunRepository runs,
                      TransactionJobClient transactions,
                      ReconciliationJobClient reconciliations) {
        this.definitions = definitions;
        this.runs = runs;
        this.transactions = transactions;
        this.reconciliations = reconciliations;
    }

    @Transactional(readOnly = true)
    public List<JobDefinition> listJobs() {
        return definitions.findAll();
    }

    /**
     * 执行作业；下游不可用时本次运行保留为 FAILED 并抛出 {@link RemoteServiceException}（接口 503）。
     */
    public JobRun execute(String jobCode, LocalDate businessDate) {
        Objects.requireNonNull(businessDate, "businessDate must not be null");
        JobDefinition definition = requireExecutable(jobCode);
        JobRun run = runs.save(JobRun.start(UUID.randomUUID().toString(), definition.getJobCode(),
                businessDate, LocalDateTime.now()));
        try {
            return runs.save(run.complete(dispatch(definition, businessDate), LocalDateTime.now()));
        } catch (RemoteServiceException ex) {
            log.warn("Job {} for {} failed: {}", jobCode, businessDate, ex.getMessage());
            runs.save(run.fail(ex.getMessage(), LocalDateTime.now()));
            throw ex;
        }
    }

    @Transactional(readOnly = true)
    public JobRun getRun(String runId) {
        return runs.findById(runId)
                .orElseThrow(() -> new NotFoundException("JOB_RUN_NOT_FOUND",
                        "Job run " + runId + " not found"));
    }

    @Transactional(readOnly = true)
    public PageResult<JobRun> listRuns(String jobCode, String status, int page, int size) {
        JobRunFilter filter = new JobRunFilter(jobCode, parseStatus(status));
        return runs.search(filter, Math.max(page, 0), size <= 0 ? DEFAULT_PAGE_SIZE : size);
    }

    private JobDefinition requireExecutable(String jobCode) {
        JobDefinition definition = definitions.findByJobCode(jobCode)
                .orElseThrow(() -> new NotFoundException("JOB_DEFINITION_NOT_FOUND",
                        "Job " + jobCode + " is not defined"));
        if (!definition.isActive()) {
            throw new BusinessRuleException("JOB_INACTIVE", "Job " + jobCode + " is inactive");
        }
        if (!SUPPORTED_TARGETS.contains(definition.getTargetService())) {
            throw new BusinessRuleException("JOB_TARGET_UNSUPPORTED",
                    "Job " + jobCode + " targets unsupported service " + definition.getTargetService());
        }
        return definition;
    }

    private String dispatch(JobDefinition definition, LocalDate businessDate) {
        if (TARGET_TRANSACTION.equals(definition.getTargetService())) {
            return transactions.runInterest(businessDate);
        }
        return reconciliations.runReconciliation(businessDate);
    }

    private static RunStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return RunStatus.valueOf(status.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new InvalidRequestException("INVALID_STATUS",
                    "Status filter must be one of RUNNING, COMPLETED, FAILED.");
        }
    }
}
