package com.carddemo.batch.application;

import com.carddemo.batch.domain.exception.BusinessRuleException;
import com.carddemo.batch.domain.exception.NotFoundException;
import com.carddemo.batch.domain.exception.RemoteServiceException;
import com.carddemo.batch.domain.model.JobRun;
import com.carddemo.batch.domain.model.RunStatus;
import com.carddemo.batch.domain.repository.PageResult;
import com.carddemo.batch.infrastructure.client.ReconciliationJobClient;
import com.carddemo.batch.infrastructure.client.TransactionJobClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 不使用测试事务：验证 RUNNING → COMPLETED/FAILED 的多次提交在真实事务边界下成立
 * （下游失败时 RUNNING 记录不能被回滚掉），并覆盖作业校验与历史分页。
 */
@SpringBootTest
@ActiveProfiles("test")
class JobExecutionPersistenceTest {

    private static final LocalDate BUSINESS_DATE = LocalDate.of(2026, 10, 26);
    private static final String INTEREST_SUMMARY = "Interest run for 2026-10-26 posted for 2 accounts, total 45.67";
    private static final String RECONCILIATION_SUMMARY = "Reconciliation run run-1 finished with status COMPLETED, mismatches 0";

    @Autowired
    private JobService jobs;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private TransactionJobClient transactionJobClient;

    @MockBean
    private ReconciliationJobClient reconciliationJobClient;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("delete from job_run");
        jdbcTemplate.update("delete from job_definition where job_code not in ('INTEREST_RUN', 'RECONCILIATION')");
    }

    private void stubSuccess() {
        when(transactionJobClient.runInterest(any())).thenReturn(INTEREST_SUMMARY);
        when(reconciliationJobClient.runReconciliation(any())).thenReturn(RECONCILIATION_SUMMARY);
    }

    @Test
    void interestJobCompletesWithDownstreamSummary() {
        stubSuccess();

        JobRun run = jobs.execute("INTEREST_RUN", BUSINESS_DATE);

        assertThat(run.getStatus()).isEqualTo(RunStatus.COMPLETED);
        assertThat(run.getMessage()).isEqualTo(INTEREST_SUMMARY);
        assertThat(run.getStartedAt()).isNotNull();
        assertThat(run.getCompletedAt()).isNotNull();

        JobRun reloaded = jobs.getRun(run.getRunId());
        assertThat(reloaded.getStatus()).isEqualTo(RunStatus.COMPLETED);
        assertThat(reloaded.getJobCode()).isEqualTo("INTEREST_RUN");
        assertThat(reloaded.getBusinessDate()).isEqualTo(BUSINESS_DATE);
        assertThat(reloaded.getMessage()).contains("posted for 2 accounts");
    }

    @Test
    void reconciliationJobCompletesWithDownstreamSummary() {
        stubSuccess();

        JobRun run = jobs.execute("RECONCILIATION", BUSINESS_DATE);

        assertThat(run.getStatus()).isEqualTo(RunStatus.COMPLETED);
        assertThat(jobs.getRun(run.getRunId()).getMessage()).contains("Reconciliation run run-1");
    }

    @Test
    void failedJobKeepsFailedRecordAndRethrows() {
        when(transactionJobClient.runInterest(any()))
                .thenThrow(new RemoteServiceException("transaction-service is unavailable"));

        assertThatThrownBy(() -> jobs.execute("INTEREST_RUN", BUSINESS_DATE))
                .isInstanceOf(RemoteServiceException.class)
                .hasMessageContaining("transaction-service is unavailable");

        PageResult<JobRun> history = jobs.listRuns("INTEREST_RUN", null, 0, 10);
        assertThat(history.total()).isEqualTo(1);
        JobRun failed = history.items().get(0);
        assertThat(failed.getStatus()).isEqualTo(RunStatus.FAILED);
        assertThat(failed.getRunId()).isNotBlank();
        assertThat(failed.getMessage()).isEqualTo("transaction-service is unavailable");
        assertThat(failed.getCompletedAt()).isNotNull();
        assertThat(failed.getBusinessDate()).isEqualTo(BUSINESS_DATE);
    }

    @Test
    void repeatedRunsOfSameJobAndDateAreAllKept() {
        stubSuccess();

        JobRun first = jobs.execute("INTEREST_RUN", BUSINESS_DATE);
        JobRun second = jobs.execute("INTEREST_RUN", BUSINESS_DATE);
        jobs.execute("RECONCILIATION", BUSINESS_DATE);

        assertThat(first.getRunId()).isNotEqualTo(second.getRunId());
        assertThat(jobs.listRuns(null, null, 0, 10).total()).isEqualTo(3);
        assertThat(jobs.listRuns("INTEREST_RUN", null, 0, 10).total()).isEqualTo(2);
        assertThat(jobs.listRuns(null, "COMPLETED", 0, 10).total()).isEqualTo(3);
        assertThat(jobs.listRuns(null, "failed", 0, 10).total()).isZero();
        assertThat(jobs.listRuns(null, null, 0, 2).items()).hasSize(2);
        assertThat(jobs.listRuns(null, null, 1, 2).total()).isEqualTo(3);
    }

    @Test
    void unknownJobIsRejectedWithoutRecordingARun() {
        assertThatThrownBy(() -> jobs.execute("MISSING_JOB", BUSINESS_DATE))
                .isInstanceOfSatisfying(NotFoundException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("JOB_DEFINITION_NOT_FOUND"));
        assertThat(jobs.listRuns(null, null, 0, 10).total()).isZero();
    }

    @Test
    void inactiveJobIsRejectedWithoutRecordingARun() {
        jdbcTemplate.update("""
                insert into job_definition (job_code, name, description, target_service, active, version)
                values ('MANUAL_JOB', '停用作业', '测试用停用作业', 'transaction', false, 0)
                """);

        assertThatThrownBy(() -> jobs.execute("MANUAL_JOB", BUSINESS_DATE))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("JOB_INACTIVE"));
        assertThat(jobs.listRuns("MANUAL_JOB", null, 0, 10).total()).isZero();
    }

    @Test
    void jobDefinitionsAreSeeded() {
        assertThat(jobs.listJobs())
                .extracting(definition -> definition.getJobCode())
                .containsExactly("INTEREST_RUN", "RECONCILIATION");
        assertThat(jobs.listJobs())
                .extracting(definition -> definition.getTargetService())
                .containsExactly("transaction", "reconciliation");
    }
}
