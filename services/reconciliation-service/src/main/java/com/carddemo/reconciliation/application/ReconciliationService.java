package com.carddemo.reconciliation.application;

import com.carddemo.reconciliation.domain.exception.NotFoundException;
import com.carddemo.reconciliation.domain.exception.RemoteServiceException;
import com.carddemo.reconciliation.domain.model.CheckType;
import com.carddemo.reconciliation.domain.model.ItemStatus;
import com.carddemo.reconciliation.domain.model.ReconciliationItem;
import com.carddemo.reconciliation.domain.model.ReconciliationRun;
import com.carddemo.reconciliation.domain.model.ReconciliationTotals;
import com.carddemo.reconciliation.domain.repository.PageResult;
import com.carddemo.reconciliation.domain.repository.ReconciliationItemRepository;
import com.carddemo.reconciliation.domain.repository.ReconciliationRunRepository;
import com.carddemo.reconciliation.infrastructure.client.LedgerClient;
import com.carddemo.reconciliation.infrastructure.client.TransactionClient;
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
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 对账用例（A1.4）：按业务日期把过账批次与 POSTING_BATCH 日记账逐笔匹配，并校验当期试算平衡。
 *
 * <p>事务边界：外部调用不能包在一个事务里，否则下游失败会把 RUNNING 记录一起回滚。
 * 因此本类不加类级 {@code @Transactional}——先落 RUNNING，计算完成后写 COMPLETED 与明细；
 * 失败时把本次运行置 FAILED、保留 error_message，再把 503 语义的异常抛给接口层。
 */
@Service
public class ReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationService.class);

    private static final String SOURCE_TYPE_POSTING_BATCH = "POSTING_BATCH";
    private static final String DIRECTION_DEBIT = "DR";
    private static final String DIRECTION_CREDIT = "CR";
    private static final BigDecimal ZERO = new BigDecimal("0.00");
    private static final int DEFAULT_PAGE_SIZE = 20;

    private final ReconciliationRunRepository runs;
    private final ReconciliationItemRepository items;
    private final TransactionClient transactions;
    private final LedgerClient ledger;

    public ReconciliationService(ReconciliationRunRepository runs,
                                 ReconciliationItemRepository items,
                                 TransactionClient transactions,
                                 LedgerClient ledger) {
        this.runs = runs;
        this.items = items;
        this.transactions = transactions;
        this.ledger = ledger;
    }

    /**
     * 执行某个业务日期的对账：读取 → 匹配 → 试算平衡 → 保存运行与明细。
     * 下游不可用时运行置 FAILED 并抛出 {@link RemoteServiceException}（接口 503）。
     */
    public ReconciliationRunView run(LocalDate businessDate) {
        Objects.requireNonNull(businessDate, "businessDate must not be null");
        YearMonth period = YearMonth.from(businessDate);
        ReconciliationRun run = runs.save(ReconciliationRun.start(
                UUID.randomUUID().toString(), businessDate, period, LocalDateTime.now()));
        try {
            List<TransactionClient.PostingBatch> batches = transactions.findBatches(businessDate);
            List<LedgerClient.JournalEntry> journals = ledger.findJournals(businessDate);
            LedgerClient.TrialBalance trialBalance = ledger.trialBalance(period);

            List<ReconciliationItem> computed = reconcile(run.getRunId(), batches, journals, trialBalance, period);
            ReconciliationRun completed = runs.save(
                    run.complete(totals(batches, journals, computed), LocalDateTime.now()));
            List<ReconciliationItem> saved = computed.stream().map(items::save).toList();
            return new ReconciliationRunView(completed, saved);
        } catch (RemoteServiceException ex) {
            log.warn("Reconciliation for {} failed: {}", businessDate, ex.getMessage());
            runs.save(run.fail(ex.getMessage(), LocalDateTime.now()));
            throw ex;
        }
    }

    @Transactional(readOnly = true)
    public ReconciliationRunView get(String runId) {
        return new ReconciliationRunView(requireRun(runId), items.findByRunId(runId));
    }

    @Transactional(readOnly = true)
    public PageResult<ReconciliationRun> list(int page, int size) {
        return runs.search(Math.max(page, 0), size <= 0 ? DEFAULT_PAGE_SIZE : size);
    }

    @Transactional(readOnly = true)
    public List<ReconciliationItem> listItems(String runId) {
        requireRun(runId);
        return items.findByRunId(runId);
    }

    /**
     * 差异处理：登记说明并标记 resolved，只更新对账记录，不触碰账务数据（设计 §5）。
     */
    @Transactional
    public ReconciliationItem resolve(String itemId, String note) {
        ReconciliationItem item = items.findById(itemId)
                .orElseThrow(() -> new NotFoundException("RECONCILIATION_ITEM_NOT_FOUND",
                        "Reconciliation item " + itemId + " not found"));
        return items.save(item.resolve(note));
    }

    private ReconciliationRun requireRun(String runId) {
        return runs.findById(runId)
                .orElseThrow(() -> new NotFoundException("RECONCILIATION_RUN_NOT_FOUND",
                        "Reconciliation run " + runId + " not found"));
    }

    /**
     * 匹配规则（设计 §2）：batchId ↔ journal.sourceId；批次金额 = 日记账借方合计 = 贷方合计。
     * 缺日记账 MISSING_JOURNAL、金额不一致 AMOUNT_MISMATCH、孤儿日记账 MISSING_BATCH，
     * 试算平衡固定产出一条 TRIAL_BALANCE 明细（MATCH 或 UNBALANCED）。
     */
    private List<ReconciliationItem> reconcile(String runId,
                                               List<TransactionClient.PostingBatch> batches,
                                               List<LedgerClient.JournalEntry> journals,
                                               LedgerClient.TrialBalance trialBalance,
                                               YearMonth period) {
        Map<String, List<LedgerClient.JournalEntry>> journalsBySource = postingJournals(journals).stream()
                .collect(Collectors.groupingBy(LedgerClient.JournalEntry::sourceId,
                        TreeMap::new, Collectors.toList()));
        List<TransactionClient.PostingBatch> sortedBatches = batches.stream()
                .sorted(Comparator.comparing(TransactionClient.PostingBatch::batchId))
                .toList();
        Set<String> batchIds = sortedBatches.stream()
                .map(TransactionClient.PostingBatch::batchId)
                .collect(Collectors.toCollection(HashSet::new));

        List<ReconciliationItem> result = new ArrayList<>();
        for (TransactionClient.PostingBatch batch : sortedBatches) {
            List<LedgerClient.JournalEntry> matched = journalsBySource.getOrDefault(batch.batchId(), List.of());
            BigDecimal expected = batch.amount();
            if (matched.isEmpty()) {
                result.add(item(runId, batch.batchId(), expected, ZERO, ItemStatus.MISSING_JOURNAL,
                        "Posting batch " + batch.batchId() + " has no journal entry"));
                continue;
            }
            BigDecimal debit = sum(matched, DIRECTION_DEBIT);
            BigDecimal credit = sum(matched, DIRECTION_CREDIT);
            boolean agreed = debit.compareTo(expected) == 0 && credit.compareTo(expected) == 0;
            result.add(item(runId, batch.batchId(), expected, debit,
                    agreed ? ItemStatus.MATCH : ItemStatus.AMOUNT_MISMATCH,
                    "Batch amount " + expected.toPlainString() + ", journal debit " + debit.toPlainString()
                            + ", journal credit " + credit.toPlainString()));
        }

        for (Map.Entry<String, List<LedgerClient.JournalEntry>> orphan : journalsBySource.entrySet()) {
            if (batchIds.contains(orphan.getKey())) {
                continue;
            }
            String entryIds = orphan.getValue().stream()
                    .map(LedgerClient.JournalEntry::entryId)
                    .collect(Collectors.joining(","));
            result.add(item(runId, orphan.getKey(), ZERO, sum(orphan.getValue(), DIRECTION_DEBIT),
                    ItemStatus.MISSING_BATCH, "Journal entry " + entryIds + " has no posting batch"));
        }

        result.add(trialBalanceItem(runId, trialBalance, period));
        return result;
    }

    private static ReconciliationItem item(String runId, String referenceId, BigDecimal expected,
                                           BigDecimal actual, ItemStatus status, String detail) {
        return ReconciliationItem.of(UUID.randomUUID().toString(), runId, CheckType.POSTING_VS_JOURNAL,
                referenceId, expected, actual, status, detail);
    }

    private static ReconciliationItem trialBalanceItem(String runId, LedgerClient.TrialBalance trialBalance,
                                                       YearMonth period) {
        String referenceId = trialBalance.period() == null ? period.toString() : trialBalance.period();
        String detail = "Total debit " + trialBalance.totalDebit().toPlainString()
                + ", total credit " + trialBalance.totalCredit().toPlainString();
        return ReconciliationItem.of(UUID.randomUUID().toString(), runId, CheckType.TRIAL_BALANCE, referenceId,
                trialBalance.totalDebit(), trialBalance.totalCredit(),
                trialBalance.balanced() ? ItemStatus.MATCH : ItemStatus.UNBALANCED, detail);
    }

    private static ReconciliationTotals totals(List<TransactionClient.PostingBatch> batches,
                                               List<LedgerClient.JournalEntry> journals,
                                               List<ReconciliationItem> computed) {
        List<LedgerClient.JournalEntry> scoped = postingJournals(journals);
        BigDecimal batchAmount = batches.stream()
                .map(TransactionClient.PostingBatch::amount)
                .reduce(ZERO, BigDecimal::add);
        BigDecimal journalDebit = scoped.stream()
                .map(entry -> sum(List.of(entry), DIRECTION_DEBIT))
                .reduce(ZERO, BigDecimal::add);
        BigDecimal journalCredit = scoped.stream()
                .map(entry -> sum(List.of(entry), DIRECTION_CREDIT))
                .reduce(ZERO, BigDecimal::add);
        int matchCount = (int) computed.stream()
                .filter(item -> item.getStatus() == ItemStatus.MATCH)
                .count();
        int missingJournalCount = (int) computed.stream()
                .filter(item -> item.getStatus() == ItemStatus.MISSING_JOURNAL)
                .count();
        int missingBatchCount = (int) computed.stream()
                .filter(item -> item.getStatus() == ItemStatus.MISSING_BATCH)
                .count();
        return new ReconciliationTotals(
                batches.size(),
                scale(batchAmount),
                scoped.size(),
                scale(journalDebit),
                scale(journalCredit),
                matchCount,
                computed.size() - matchCount,
                missingJournalCount,
                missingBatchCount);
    }

    private static List<LedgerClient.JournalEntry> postingJournals(List<LedgerClient.JournalEntry> journals) {
        return journals.stream()
                .filter(entry -> entry.sourceType() != null
                        && SOURCE_TYPE_POSTING_BATCH.equals(entry.sourceType().toUpperCase(Locale.ROOT)))
                .toList();
    }

    private static BigDecimal sum(List<LedgerClient.JournalEntry> entries, String direction) {
        BigDecimal total = ZERO;
        for (LedgerClient.JournalEntry entry : entries) {
            for (LedgerClient.JournalLine line : entry.lines()) {
                if (line.amount() != null && direction.equalsIgnoreCase(line.direction())) {
                    total = total.add(line.amount());
                }
            }
        }
        return scale(total);
    }

    private static BigDecimal scale(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP);
    }
}
