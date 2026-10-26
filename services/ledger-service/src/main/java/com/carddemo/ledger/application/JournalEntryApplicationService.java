package com.carddemo.ledger.application;

import com.carddemo.ledger.domain.exception.BusinessRuleException;
import com.carddemo.ledger.domain.exception.NotFoundException;
import com.carddemo.ledger.domain.model.AccountingPeriod;
import com.carddemo.ledger.domain.model.EntryStatus;
import com.carddemo.ledger.domain.model.JournalEntry;
import com.carddemo.ledger.domain.model.JournalLineDraft;
import com.carddemo.ledger.domain.model.LedgerAccount;
import com.carddemo.ledger.domain.repository.AccountingPeriodRepository;
import com.carddemo.ledger.domain.repository.JournalEntryRepository;
import com.carddemo.ledger.domain.repository.LedgerAccountRepository;
import com.carddemo.ledger.domain.repository.PageResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * 日记账用例：记账（按业务键幂等）、查询、冲正。
 */
@Service
@Transactional
public class JournalEntryApplicationService {

    private static final int DEFAULT_PAGE_SIZE = 20;

    private final JournalEntryRepository entries;
    private final LedgerAccountRepository accounts;
    private final AccountingPeriodRepository periods;

    public JournalEntryApplicationService(JournalEntryRepository entries,
                                          LedgerAccountRepository accounts,
                                          AccountingPeriodRepository periods) {
        this.entries = entries;
        this.accounts = accounts;
        this.periods = periods;
    }

    /**
     * 记账：同一 (sourceType, sourceId) 内容一致返回原分录，内容不一致报 409；
     * 科目必须存在且启用，posted_at 所在期间必须存在且未关账。
     */
    public JournalEntry post(PostJournalEntryCommand command) {
        String sourceType = normalizeSourceType(command.sourceType());
        String sourceId = requireSourceId(command.sourceId());
        LocalDateTime postedAt = Objects.requireNonNull(command.postedAt(), "postedAt must not be null");
        List<JournalLineDraft> drafts = normalizeDrafts(command.lines());

        Optional<JournalEntry> existing = entries.findBySourceTypeAndSourceId(sourceType, sourceId);
        if (existing.isPresent()) {
            JournalEntry entry = existing.get();
            if (!entry.matchesContent(command.description(), postedAt, drafts)) {
                throw new BusinessRuleException("JOURNAL_SOURCE_CONFLICT",
                        "Journal source " + sourceType + "/" + sourceId
                                + " already recorded with different content.");
            }
            return entry;
        }

        JournalEntry entry = JournalEntry.post(UUID.randomUUID().toString(), sourceType, sourceId,
                command.description(), postedAt, drafts, LocalDateTime.now());
        drafts.forEach(draft -> requireActiveAccount(draft.accountCode()));
        requireOpenPeriod(postedAt);
        return entries.save(entry);
    }

    @Transactional(readOnly = true)
    public JournalEntry get(String entryId) {
        return entries.findByEntryId(entryId)
                .orElseThrow(() -> new NotFoundException("JOURNAL_ENTRY_NOT_FOUND",
                        "Journal entry " + entryId + " not found"));
    }

    @Transactional(readOnly = true)
    public PageResult<JournalEntry> list(String sourceId, int page, int size) {
        int safeSize = size <= 0 ? DEFAULT_PAGE_SIZE : size;
        return entries.search(sourceId, Math.max(page, 0), safeSize);
    }

    /**
     * 冲正：生成 sourceType=REVERSAL、sourceId=原分录号的反向分录，并把原分录标记 REVERSED。
     * 重复冲正返回已有冲正分录。
     */
    public JournalEntry reverse(String entryId, LocalDateTime postedAt) {
        JournalEntry original = entries.findByEntryId(entryId)
                .orElseThrow(() -> new NotFoundException("JOURNAL_ENTRY_NOT_FOUND",
                        "Journal entry " + entryId + " not found"));
        if (original.getStatus() == EntryStatus.REVERSED) {
            return entries.findBySourceTypeAndSourceId(JournalEntry.REVERSAL_SOURCE_TYPE, entryId)
                    .orElseThrow(() -> new BusinessRuleException("JOURNAL_ALREADY_REVERSED",
                            "Journal entry " + entryId + " is already reversed."));
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime effectivePostedAt = postedAt == null ? now : postedAt;
        requireOpenPeriod(effectivePostedAt);
        JournalEntry reversal = original.reverse(UUID.randomUUID().toString(), effectivePostedAt, now);
        entries.save(original);
        return entries.save(reversal);
    }

    private void requireActiveAccount(String accountCode) {
        LedgerAccount account = accounts.findByAccountCode(accountCode)
                .orElseThrow(() -> new NotFoundException("LEDGER_ACCOUNT_NOT_FOUND",
                        "Ledger account " + accountCode + " not found"));
        if (!account.isActive()) {
            throw new BusinessRuleException("LEDGER_ACCOUNT_INACTIVE",
                    "Ledger account " + accountCode + " is inactive.");
        }
    }

    private void requireOpenPeriod(LocalDateTime postedAt) {
        String period = YearMonth.from(postedAt).toString();
        AccountingPeriod accountingPeriod = periods.findByPeriod(period)
                .orElseThrow(() -> new NotFoundException("PERIOD_NOT_FOUND",
                        "Accounting period " + period + " not found"));
        if (!accountingPeriod.isOpen()) {
            throw new BusinessRuleException("PERIOD_CLOSED",
                    "Accounting period " + period + " is closed.");
        }
    }

    private static List<JournalLineDraft> normalizeDrafts(List<JournalLineDraft> lines) {
        if (lines == null) {
            return List.of();
        }
        return lines.stream()
                .map(line -> new JournalLineDraft(
                        line.accountCode() == null ? "" : line.accountCode().trim(),
                        line.direction(), line.amount()))
                .toList();
    }

    private static String normalizeSourceType(String sourceType) {
        if (sourceType == null || sourceType.isBlank()) {
            throw new BusinessRuleException("INVALID_JOURNAL_ENTRY", "Journal entry sourceType must be supplied.");
        }
        return sourceType.trim().toUpperCase(Locale.ROOT);
    }

    private static String requireSourceId(String sourceId) {
        if (sourceId == null || sourceId.isBlank()) {
            throw new BusinessRuleException("INVALID_JOURNAL_ENTRY", "Journal entry sourceId must be supplied.");
        }
        return sourceId.trim();
    }
}
