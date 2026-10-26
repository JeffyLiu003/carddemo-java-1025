package com.carddemo.transaction.domain.model;

import com.carddemo.transaction.domain.exception.BusinessRuleException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 过账批次聚合根（POSTING_BATCH）。持有分录列表，负责：
 * <ul>
 *   <li>按过账规则生成分录并断言借贷平衡（契约 §2.3 UNBALANCED_POSTING）；</li>
 *   <li>生成冲正批次（方向互换、幂等键 reversal:&lt;原批次号&gt;），原批次标记 REVERSED（契约 §2.3 冲正）。</li>
 * </ul>
 */
@Entity
@Table(name = "posting_batch")
public class PostingBatch {

    private static final BigDecimal MAX_AMOUNT = new BigDecimal("99999999.99");
    private static final BigDecimal ZERO = new BigDecimal("0.00");
    private static final String REVERSAL_IDEMPOTENCY_PREFIX = "reversal:";

    @Id
    @Column(name = "batch_id", length = 36, nullable = false, updatable = false)
    private String batchId;

    @Column(name = "idempotency_key", length = 80, nullable = false, updatable = false)
    private String idempotencyKey;

    @Column(name = "type_code", length = 20, nullable = false, updatable = false)
    private String typeCode;

    @Column(name = "account_id", length = 11, nullable = false, updatable = false)
    private String accountId;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal amount;

    @Column(name = "currency", length = 3, nullable = false)
    private String currency;

    @Column(name = "description", length = 200)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 10, nullable = false)
    private PostingStatus status;

    @Column(name = "reversal_of", length = 36, updatable = false)
    private String reversalOf;

    @Column(name = "ledger_entry_id", length = 64)
    private String ledgerEntryId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "reversed_at")
    private LocalDateTime reversedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @OneToMany(mappedBy = "batch", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("legNo ASC")
    private List<PostingLeg> legs = new ArrayList<>();

    protected PostingBatch() {
        // for JPA
    }

    /**
     * 按类型规则生成过账批次：每条规则一条分录，金额等于批次金额。
     */
    public static PostingBatch post(String batchId, String idempotencyKey, PostingType type,
                                    String accountId, BigDecimal amount, String currency,
                                    String description, List<PostingRule> rules, LocalDateTime postedAt) {
        BigDecimal normalized = requireAmount(amount);
        if (rules == null || rules.isEmpty()) {
            throw new BusinessRuleException("UNBALANCED_POSTING",
                    "Posting type " + type.getTypeCode() + " has no posting rules configured.");
        }
        LocalDateTime now = postedAt == null ? LocalDateTime.now() : postedAt;
        PostingBatch batch = new PostingBatch();
        batch.batchId = batchId;
        batch.idempotencyKey = idempotencyKey;
        batch.typeCode = type.getTypeCode();
        batch.accountId = accountId;
        batch.amount = normalized;
        batch.currency = currency;
        batch.description = description;
        batch.status = PostingStatus.POSTED;
        batch.createdAt = now;
        for (PostingRule rule : rules) {
            batch.addLeg(new PostingLeg(rule.getLegNo(), rule.getAccountRole(), rule.getDirection(),
                    rule.getLedgerAccountCode(), normalized, now));
        }
        batch.assertBalanced();
        return batch;
    }

    /**
     * 生成冲正批次：分录方向互换，原批次标记 REVERSED（不删除）。
     */
    public PostingBatch reverse(String reversalBatchId, LocalDateTime reversedAt) {
        if (reversalOf != null) {
            throw new BusinessRuleException("REVERSAL_NOT_ALLOWED",
                    "Batch " + batchId + " is a reversal batch and cannot be reversed.");
        }
        if (status == PostingStatus.REVERSED) {
            throw new BusinessRuleException("REVERSAL_NOT_ALLOWED",
                    "Batch " + batchId + " is already reversed.");
        }
        LocalDateTime now = reversedAt == null ? LocalDateTime.now() : reversedAt;
        PostingBatch reversal = new PostingBatch();
        reversal.batchId = reversalBatchId;
        reversal.idempotencyKey = REVERSAL_IDEMPOTENCY_PREFIX + batchId;
        reversal.typeCode = typeCode;
        reversal.accountId = accountId;
        reversal.amount = amount;
        reversal.currency = currency;
        reversal.description = "Reversal of " + batchId;
        reversal.status = PostingStatus.POSTED;
        reversal.reversalOf = batchId;
        reversal.createdAt = now;
        for (PostingLeg leg : legs) {
            reversal.addLeg(new PostingLeg(leg.getLegNo(), leg.getAccountRole(), leg.getDirection().reverse(),
                    leg.getLedgerAccountCode(), leg.getAmount(), now));
        }
        reversal.assertBalanced();

        this.status = PostingStatus.REVERSED;
        this.reversedAt = now;
        return reversal;
    }

    /**
     * 总账同步成功时回写日记账分录号；失败时保持为空（尽力而为，契约 §2.3）。
     */
    public void markLedgerEntry(String ledgerEntryId) {
        this.ledgerEntryId = ledgerEntryId;
    }

    public boolean isReversal() {
        return reversalOf != null;
    }

    private void addLeg(PostingLeg leg) {
        leg.attach(this);
        legs.add(leg);
    }

    /**
     * Σ 借方 = Σ 贷方 且均等于批次金额，否则 UNBALANCED_POSTING。
     */
    private void assertBalanced() {
        BigDecimal debit = ZERO;
        BigDecimal credit = ZERO;
        for (PostingLeg leg : legs) {
            if (leg.getDirection() == Direction.DR) {
                debit = debit.add(leg.getAmount());
            } else {
                credit = credit.add(leg.getAmount());
            }
        }
        if (debit.compareTo(credit) != 0 || debit.compareTo(amount) != 0) {
            throw new BusinessRuleException("UNBALANCED_POSTING",
                    "Posting batch is not balanced: debit " + debit + " / credit " + credit
                            + " for amount " + amount + ".");
        }
    }

    private static BigDecimal requireAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new BusinessRuleException("INVALID_POSTING_AMOUNT",
                    "Posting amount must be greater than zero.");
        }
        if (amount.scale() > 2) {
            throw new BusinessRuleException("INVALID_POSTING_AMOUNT",
                    "Posting amount must have at most 2 decimal places.");
        }
        if (amount.compareTo(MAX_AMOUNT) > 0) {
            throw new BusinessRuleException("INVALID_POSTING_AMOUNT", "Posting amount is out of range.");
        }
        return amount.setScale(2);
    }

    public String getBatchId() {
        return batchId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getTypeCode() {
        return typeCode;
    }

    public String getAccountId() {
        return accountId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public String getDescription() {
        return description;
    }

    public PostingStatus getStatus() {
        return status;
    }

    public String getReversalOf() {
        return reversalOf;
    }

    public String getLedgerEntryId() {
        return ledgerEntryId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getReversedAt() {
        return reversedAt;
    }

    public long getVersion() {
        return version;
    }

    public List<PostingLeg> getLegs() {
        return legs;
    }
}
