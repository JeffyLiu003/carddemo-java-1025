package com.carddemo.reporting.application;

import com.carddemo.reporting.domain.exception.BusinessRuleException;
import com.carddemo.reporting.domain.exception.NotFoundException;
import com.carddemo.reporting.domain.exception.RemoteServiceException;
import com.carddemo.reporting.domain.model.ReportDefinition;
import com.carddemo.reporting.domain.model.ReportRow;
import com.carddemo.reporting.domain.model.ReportRun;
import com.carddemo.reporting.domain.repository.PageResult;
import com.carddemo.reporting.domain.repository.ReportDefinitionRepository;
import com.carddemo.reporting.domain.repository.ReportRowRepository;
import com.carddemo.reporting.domain.repository.ReportRunRepository;
import com.carddemo.reporting.infrastructure.client.LedgerTrialBalanceClient;
import com.carddemo.reporting.infrastructure.client.TransactionReportClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;

/**
 * 报表用例（P4）：按报表定义读取下游、汇总成 report_row，并把本次运行落成 report_run。
 *
 * <p>事务边界：generate 不加 {@code @Transactional}——下游调用不能和 RUNNING 记录同事务，
 * 否则下游失败会把 RUNNING 一起回滚。先落 RUNNING，成功后写 COMPLETED 与结果行；
 * 失败时把本次运行置 FAILED、保留 message，再把 503 语义的异常抛给接口层。
 */
@Service
public class ReportService {

    private static final Logger log = LoggerFactory.getLogger(ReportService.class);

    private static final String SOURCE_TRANSACTION = "transaction";
    private static final String SOURCE_LEDGER = "ledger";
    private static final String TOTAL_DIMENSION = "TOTAL";
    private static final String UNKNOWN_CARD = "UNKNOWN";
    private static final BigDecimal ZERO = new BigDecimal("0.00");
    private static final int DEFAULT_PAGE_SIZE = 20;

    private final ReportDefinitionRepository definitions;
    private final ReportRunRepository runs;
    private final ReportRowRepository rows;
    private final TransactionReportClient transactions;
    private final LedgerTrialBalanceClient ledger;

    public ReportService(ReportDefinitionRepository definitions,
                         ReportRunRepository runs,
                         ReportRowRepository rows,
                         TransactionReportClient transactions,
                         LedgerTrialBalanceClient ledger) {
        this.definitions = definitions;
        this.runs = runs;
        this.rows = rows;
        this.transactions = transactions;
        this.ledger = ledger;
    }

    @Transactional(readOnly = true)
    public List<ReportDefinition> listDefinitions() {
        return definitions.findAll();
    }

    /**
     * 生成报表：定义必须存在且启用；下游不可用时本次运行保留为 FAILED 并抛出
     * {@link RemoteServiceException}（接口 503）。
     */
    public ReportRunView generate(String reportCode, LocalDate businessDate) {
        Objects.requireNonNull(businessDate, "businessDate must not be null");
        ReportDefinition definition = requireExecutable(reportCode);
        ReportRun run = runs.save(ReportRun.start(UUID.randomUUID().toString(), definition.getReportCode(),
                businessDate, LocalDateTime.now()));
        try {
            List<ReportRow> computed = generateRows(run.getRunId(), definition, businessDate);
            List<ReportRow> saved = computed.stream().map(rows::save).toList();
            ReportRun completed = runs.save(run.complete(saved.size(),
                    "Generated " + saved.size() + " rows for " + businessDate, LocalDateTime.now()));
            return new ReportRunView(completed, saved);
        } catch (RemoteServiceException ex) {
            log.warn("Report {} for {} failed: {}", reportCode, businessDate, ex.getMessage());
            runs.save(run.fail(ex.getMessage(), LocalDateTime.now()));
            throw ex;
        }
    }

    @Transactional(readOnly = true)
    public ReportRunView get(String runId) {
        ReportRun run = runs.findById(runId)
                .orElseThrow(() -> new NotFoundException("REPORT_RUN_NOT_FOUND",
                        "Report run " + runId + " not found"));
        return new ReportRunView(run, rows.findByRunId(runId));
    }

    @Transactional(readOnly = true)
    public PageResult<ReportRun> listRuns(String reportCode, int page, int size) {
        return runs.search(reportCode, Math.max(page, 0), size <= 0 ? DEFAULT_PAGE_SIZE : size);
    }

    private ReportDefinition requireExecutable(String reportCode) {
        ReportDefinition definition = definitions.findById(reportCode)
                .orElseThrow(() -> new NotFoundException("REPORT_DEFINITION_NOT_FOUND",
                        "Report " + reportCode + " is not defined"));
        if (!definition.isActive()) {
            throw new BusinessRuleException("REPORT_INACTIVE", "Report " + reportCode + " is inactive");
        }
        String source = definition.getSourceService() == null
                ? "" : definition.getSourceService().trim().toLowerCase(Locale.ROOT);
        if (!SOURCE_TRANSACTION.equals(source) && !SOURCE_LEDGER.equals(source)) {
            throw new BusinessRuleException("REPORT_SOURCE_UNSUPPORTED",
                    "Report " + reportCode + " targets unsupported service " + definition.getSourceService());
        }
        return definition;
    }

    private List<ReportRow> generateRows(String runId, ReportDefinition definition, LocalDate businessDate) {
        String source = definition.getSourceService().trim().toLowerCase(Locale.ROOT);
        if (SOURCE_TRANSACTION.equals(source)) {
            return transactionSummaryRows(runId, businessDate);
        }
        return trialBalanceRows(runId, businessDate);
    }

    /**
     * 交易汇总：每卡一行（dimension=卡号、amount=该卡合计），最后追加一行 TOTAL。
     */
    private List<ReportRow> transactionSummaryRows(String runId, LocalDate businessDate) {
        TransactionReportClient.TransactionReport report = transactions.transactionsOn(businessDate);
        Map<String, BigDecimal> amountsByCard = new TreeMap<>();
        Map<String, Integer> countsByCard = new TreeMap<>();
        for (TransactionReportClient.TransactionLine line : report.items()) {
            String cardNumber = line.cardNumber() == null || line.cardNumber().isBlank()
                    ? UNKNOWN_CARD : line.cardNumber();
            amountsByCard.merge(cardNumber, line.amount(), BigDecimal::add);
            countsByCard.merge(cardNumber, 1, Integer::sum);
        }

        List<ReportRow> result = new ArrayList<>();
        int lineNo = 1;
        BigDecimal total = ZERO;
        int transactionCount = 0;
        for (Map.Entry<String, BigDecimal> entry : amountsByCard.entrySet()) {
            int count = countsByCard.getOrDefault(entry.getKey(), 0);
            result.add(ReportRow.of(UUID.randomUUID().toString(), runId, lineNo++, entry.getKey(),
                    scale(entry.getValue()), count + " transactions"));
            total = total.add(entry.getValue());
            transactionCount += count;
        }
        result.add(ReportRow.of(UUID.randomUUID().toString(), runId, lineNo, TOTAL_DIMENSION, scale(total),
                transactionCount + " transactions across " + amountsByCard.size() + " cards"));
        return List.copyOf(result);
    }

    /**
     * 试算平衡：每科目一行（dimension=科目号、amount=净额、detail=借贷合计）。
     */
    private List<ReportRow> trialBalanceRows(String runId, LocalDate businessDate) {
        LedgerTrialBalanceClient.TrialBalance trialBalance = ledger.trialBalance(YearMonth.from(businessDate));
        List<ReportRow> result = new ArrayList<>();
        int lineNo = 1;
        for (LedgerTrialBalanceClient.TrialBalanceItem item : trialBalance.items()) {
            result.add(ReportRow.of(UUID.randomUUID().toString(), runId, lineNo++, item.accountCode(),
                    scale(item.netAmount()),
                    "debit " + item.debitTotal().toPlainString() + ", credit "
                            + item.creditTotal().toPlainString()));
        }
        return List.copyOf(result);
    }

    private static BigDecimal scale(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP);
    }
}
