package com.carddemo.ledger.domain.model;

import com.carddemo.ledger.domain.exception.BusinessRuleException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * 日记账分录聚合根。不变量：至少两行、每行金额为正、借贷合计相等、行号唯一。
 * 冲正不删除原分录，而是生成借贷方向互换的分录并把原分录标记 REVERSED。
 */
@Entity
@Table(name = "journal_entry")
public class JournalEntry {

    public static final String REVERSAL_SOURCE_TYPE = "REVERSAL";

    private static final BigDecimal ZERO = new BigDecimal("0.00");
    private static final int DESCRIPTION_MAX_LENGTH = 255;

    @Id
    @Column(name = "entry_id", length = 36, nullable = false, updatable = false)
    private String entryId;

    @Column(name = "source_type", length = 20, nullable = false, updatable = false)
    private String sourceType;

    @Column(name = "source_id", length = 64, nullable = false, updatable = false)
    private String sourceId;

    @Column(name = "description", length = DESCRIPTION_MAX_LENGTH)
    private String description;

    @Column(name = "posted_at", nullable = false, updatable = false)
    private LocalDateTime postedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 16, nullable = false)
    private EntryStatus status;

    @Column(name = "reversal_of", length = 36)
    private String reversalOf;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @OneToMany(mappedBy = "entry", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNo ASC")
    private List<JournalLine> lines = new ArrayList<>();

    protected JournalEntry() {
        // for JPA
    }

    private JournalEntry(String entryId, String sourceType, String sourceId, String description,
                         LocalDateTime postedAt, LocalDateTime createdAt) {
        this.entryId = entryId;
        this.sourceType = sourceType;
        this.sourceId = sourceId;
        this.description = description;
        this.postedAt = postedAt;
        this.status = EntryStatus.POSTED;
        this.createdAt = createdAt;
    }

    /**
     * 记一笔日记账。校验失败抛出 409 语义的 {@link BusinessRuleException}。
     */
    public static JournalEntry post(String entryId, String sourceType, String sourceId, String description,
                                    LocalDateTime postedAt, List<JournalLineDraft> drafts,
                                    LocalDateTime createdAt) {
        Objects.requireNonNull(postedAt, "postedAt must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        JournalEntry entry = new JournalEntry(entryId,
                requireText(sourceType, "sourceType").toUpperCase(Locale.ROOT),
                requireText(sourceId, "sourceId"), normalizeDescription(description), postedAt, createdAt);
        entry.appendLines(drafts);
        return entry;
    }

    /**
     * 冲正：生成 sourceType=REVERSAL、sourceId=本分录号的分录，借贷方向互换，金额不变。
     */
    public JournalEntry reverse(String reversalEntryId, LocalDateTime postedAt, LocalDateTime createdAt) {
        if (status != EntryStatus.POSTED) {
            throw new BusinessRuleException("JOURNAL_ALREADY_REVERSED",
                    "Journal entry " + entryId + " is " + status + " and cannot be reversed.");
        }
        List<JournalLineDraft> drafts = lines.stream()
                .map(line -> new JournalLineDraft(line.getAccountCode(),
                        line.getDirection().opposite(), line.getAmount()))
                .toList();
        JournalEntry reversal = post(reversalEntryId, REVERSAL_SOURCE_TYPE, entryId,
                "Reversal of " + entryId, postedAt, drafts, createdAt);
        reversal.reversalOf = entryId;
        this.status = EntryStatus.REVERSED;
        return reversal;
    }

    /**
     * 幂等判定：业务键相同且内容（摘要、记账时间、行金额/方向/科目）一致时返回原分录。
     */
    public boolean matchesContent(String description, LocalDateTime postedAt, List<JournalLineDraft> drafts) {
        return Objects.equals(this.description, normalizeDescription(description))
                && Objects.equals(this.postedAt, postedAt)
                && signature(lines).equals(draftSignature(drafts));
    }

    private void appendLines(List<JournalLineDraft> drafts) {
        if (drafts == null || drafts.size() < 2) {
            throw new BusinessRuleException("UNBALANCED_JOURNAL",
                    "A journal entry must contain at least two lines.");
        }
        BigDecimal debitTotal = ZERO;
        BigDecimal creditTotal = ZERO;
        int lineNo = 1;
        for (JournalLineDraft draft : drafts) {
            Objects.requireNonNull(draft, "journal line must not be null");
            String accountCode = requireText(draft.accountCode(), "accountCode");
            Direction direction = Objects.requireNonNull(draft.direction(), "line direction must not be null");
            if (draft.amount() == null || draft.amount().signum() <= 0) {
                throw new BusinessRuleException("INVALID_JOURNAL_AMOUNT",
                        "Journal line amount must be greater than zero.");
            }
            BigDecimal amount = draft.amount().setScale(2, RoundingMode.HALF_UP);
            addLine(new JournalLine(UUID.randomUUID().toString(), this, lineNo, accountCode, direction, amount));
            lineNo++;
            if (direction == Direction.DR) {
                debitTotal = debitTotal.add(amount);
            } else {
                creditTotal = creditTotal.add(amount);
            }
        }
        if (debitTotal.compareTo(creditTotal) != 0) {
            throw new BusinessRuleException("UNBALANCED_JOURNAL",
                    "Journal entry is not balanced: debit " + debitTotal + " vs credit " + creditTotal + ".");
        }
    }

    private void addLine(JournalLine line) {
        boolean duplicated = lines.stream().anyMatch(existing -> existing.getLineNo() == line.getLineNo());
        if (duplicated) {
            throw new BusinessRuleException("DUPLICATE_LINE_NO",
                    "Journal line number " + line.getLineNo() + " is duplicated.");
        }
        lines.add(line);
    }

    private static List<String> signature(Collection<JournalLine> lines) {
        return lines.stream()
                .map(line -> signatureOf(line.getAccountCode(), line.getDirection(), line.getAmount()))
                .sorted()
                .toList();
    }

    private static List<String> draftSignature(List<JournalLineDraft> drafts) {
        return drafts.stream()
                .filter(Objects::nonNull)
                .map(draft -> signatureOf(draft.accountCode(), draft.direction(), draft.amount()))
                .sorted()
                .toList();
    }

    private static String signatureOf(String accountCode, Direction direction, BigDecimal amount) {
        return accountCode + "|" + direction + "|"
                + (amount == null ? "null" : amount.setScale(2, RoundingMode.HALF_UP).toPlainString());
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new BusinessRuleException("INVALID_JOURNAL_ENTRY",
                    "Journal entry " + field + " must be supplied.");
        }
        return value.trim();
    }

    private static String normalizeDescription(String description) {
        if (description == null || description.isBlank()) {
            return null;
        }
        String trimmed = description.trim();
        return trimmed.length() > DESCRIPTION_MAX_LENGTH ? trimmed.substring(0, DESCRIPTION_MAX_LENGTH) : trimmed;
    }

    public String getEntryId() {
        return entryId;
    }

    public String getSourceType() {
        return sourceType;
    }

    public String getSourceId() {
        return sourceId;
    }

    public String getDescription() {
        return description;
    }

    public LocalDateTime getPostedAt() {
        return postedAt;
    }

    public EntryStatus getStatus() {
        return status;
    }

    public String getReversalOf() {
        return reversalOf;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public long getVersion() {
        return version;
    }

    public List<JournalLine> getLines() {
        return List.copyOf(lines);
    }
}
