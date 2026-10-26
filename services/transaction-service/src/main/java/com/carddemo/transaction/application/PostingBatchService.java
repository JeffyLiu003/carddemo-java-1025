package com.carddemo.transaction.application;

import com.carddemo.transaction.domain.exception.BusinessRuleException;
import com.carddemo.transaction.domain.exception.InvalidRequestException;
import com.carddemo.transaction.domain.exception.NotFoundException;
import com.carddemo.transaction.domain.model.AccountEffect;
import com.carddemo.transaction.domain.model.AccountSnapshot;
import com.carddemo.transaction.domain.model.PostingBatch;
import com.carddemo.transaction.domain.model.PostingRule;
import com.carddemo.transaction.domain.model.PostingStatus;
import com.carddemo.transaction.domain.model.PostingType;
import com.carddemo.transaction.domain.repository.PageResult;
import com.carddemo.transaction.domain.repository.PostingBatchFilter;
import com.carddemo.transaction.domain.repository.PostingBatchRepository;
import com.carddemo.transaction.domain.repository.PostingRuleRepository;
import com.carddemo.transaction.domain.repository.PostingTypeRepository;
import com.carddemo.transaction.infrastructure.client.AccountClient;
import com.carddemo.transaction.infrastructure.client.LedgerClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 过账引擎用例（A1.2）：
 * 幂等创建（同 key 同内容返回原批次）、按规则生成借贷分录、调用账户变更余额、
 * 尽力同步总账、查询与冲正。契约见 docs/design/11-money-path-slice-a2.md §2.3。
 */
@Service
@Transactional
public class PostingBatchService {

    private static final Logger log = LoggerFactory.getLogger(PostingBatchService.class);
    private static final String DEFAULT_CURRENCY = "USD";
    private static final String ACTIVE = "Y";
    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final String LEDGER_SOURCE_TYPE = "POSTING_BATCH";

    private final PostingTypeRepository postingTypes;
    private final PostingRuleRepository postingRules;
    private final PostingBatchRepository postingBatches;
    private final AccountClient accountClient;
    private final LedgerClient ledgerClient;
    private final boolean ledgerSyncEnabled;

    public PostingBatchService(PostingTypeRepository postingTypes,
                               PostingRuleRepository postingRules,
                               PostingBatchRepository postingBatches,
                               AccountClient accountClient,
                               LedgerClient ledgerClient,
                               @Value("${ledger.sync-enabled:true}") boolean ledgerSyncEnabled) {
        this.postingTypes = postingTypes;
        this.postingRules = postingRules;
        this.postingBatches = postingBatches;
        this.accountClient = accountClient;
        this.ledgerClient = ledgerClient;
        this.ledgerSyncEnabled = ledgerSyncEnabled;
    }

    /**
     * 创建过账批次：校验幂等键与金额 → 幂等返回/冲突 → 校验类型与账户 →
     * 生成并校验借贷分录 → 调账户记账 → 落库 → 尽力同步总账。
     */
    public PostingBatchView create(CreatePostingBatchCommand command) {
        String idempotencyKey = requireIdempotencyKey(command.idempotencyKey());
        BigDecimal amount = requireAmount(command.amount());
        String typeCode = trimToNull(command.typeCode());
        String accountId = trimToNull(command.accountId());
        if (typeCode == null) {
            throw new NotFoundException("POSTING_TYPE_NOT_FOUND", "Posting type must be supplied.");
        }
        if (accountId == null) {
            throw new NotFoundException("ACCOUNT_NOT_FOUND", "Account number must be supplied.");
        }

        Optional<PostingBatch> duplicate = postingBatches.findByIdempotencyKey(idempotencyKey);
        if (duplicate.isPresent()) {
            return existingView(duplicate.get(), typeCode, accountId, amount);
        }

        PostingType type = postingTypes.findType(typeCode)
                .orElseThrow(() -> new NotFoundException("POSTING_TYPE_NOT_FOUND",
                        "Posting type " + typeCode + " not found"));
        if (!type.isActive()) {
            throw new BusinessRuleException("POSTING_TYPE_INACTIVE",
                    "Posting type " + typeCode + " is inactive.");
        }
        List<PostingRule> rules = postingRules.findRules(typeCode);
        AccountSnapshot account = accountClient.findAccount(accountId)
                .orElseThrow(() -> new NotFoundException("ACCOUNT_NOT_FOUND",
                        "Account " + accountId + " not found"));
        if (!ACTIVE.equalsIgnoreCase(account.activeStatus())) {
            throw new BusinessRuleException("ACCOUNT_INACTIVE", "Account " + accountId + " is inactive.");
        }

        String currency = trimToNull(command.currency());
        PostingBatch batch = PostingBatch.post(UUID.randomUUID().toString(), idempotencyKey, type,
                account.accountId(), amount, currency == null ? DEFAULT_CURRENCY : currency.toUpperCase(),
                trimToNull(command.description()), rules, LocalDateTime.now());

        // 账户调用成功后才保存批次（契约 §2.3）
        accountClient.applyPosting(account.accountId(), type.getAccountEffect().apply(amount));
        PostingBatch saved = postingBatches.saveBatch(batch);
        syncLedger(saved);
        return new PostingBatchView(saved, type.getName());
    }

    @Transactional(readOnly = true)
    public PostingBatchView get(String batchId) {
        PostingBatch batch = postingBatches.findByBatchId(batchId)
                .orElseThrow(() -> new NotFoundException("POSTING_BATCH_NOT_FOUND",
                        "Posting batch " + batchId + " not found"));
        return view(batch);
    }

    @Transactional(readOnly = true)
    public PageResult<PostingBatchView> list(String accountId, String idempotencyKey, int page, int size) {
        PostingBatchFilter filter = new PostingBatchFilter(trimToNull(accountId), trimToNull(idempotencyKey));
        PageResult<PostingBatch> result = postingBatches.search(filter,
                Math.max(page, 0), size <= 0 ? DEFAULT_PAGE_SIZE : size);
        Map<String, String> typeNames = postingTypes.findAllTypes().stream()
                .collect(Collectors.toMap(PostingType::getTypeCode, PostingType::getName, (left, right) -> left));
        return new PageResult<>(result.items().stream()
                .map(batch -> new PostingBatchView(batch, typeNames.get(batch.getTypeCode())))
                .toList(), result.total());
    }

    /**
     * 冲正：已冲正返回已有冲正批次；冲正批次不可再冲正；否则生成方向互换的冲正批次并反向调整账户。
     */
    public PostingBatchView reverse(String batchId) {
        PostingBatch original = postingBatches.findByBatchId(batchId)
                .orElseThrow(() -> new NotFoundException("POSTING_BATCH_NOT_FOUND",
                        "Posting batch " + batchId + " not found"));
        PostingType type = postingTypes.findType(original.getTypeCode())
                .orElseThrow(() -> new NotFoundException("POSTING_TYPE_NOT_FOUND",
                        "Posting type " + original.getTypeCode() + " not found"));
        if (original.isReversal()) {
            throw new BusinessRuleException("REVERSAL_NOT_ALLOWED",
                    "Batch " + batchId + " is a reversal batch and cannot be reversed.");
        }
        if (original.getStatus() == PostingStatus.REVERSED) {
            PostingBatch existing = postingBatches.findByReversalOf(batchId)
                    .orElseThrow(() -> new BusinessRuleException("REVERSAL_NOT_ALLOWED",
                            "Batch " + batchId + " is already reversed."));
            return new PostingBatchView(existing, type.getName());
        }

        PostingBatch reversal = original.reverse(UUID.randomUUID().toString(), LocalDateTime.now());
        AccountEffect effect = type.getAccountEffect();
        accountClient.applyPosting(original.getAccountId(), effect.apply(original.getAmount()).negate());
        postingBatches.saveBatch(original);
        PostingBatch saved = postingBatches.saveBatch(reversal);
        syncLedger(saved);
        return new PostingBatchView(saved, type.getName());
    }

    /**
     * 总账补同步：用于总账短暂不可用后补齐批次分录；已同步的批次直接返回。
     */
    public PostingBatchView resyncLedger(String batchId) {
        PostingBatch batch = postingBatches.findByBatchId(batchId)
                .orElseThrow(() -> new NotFoundException("POSTING_BATCH_NOT_FOUND",
                        "Posting batch " + batchId + " not found"));
        if (batch.getLedgerEntryId() == null) {
            syncLedger(batch);
            batch = postingBatches.saveBatch(batch);
        }
        return view(batch);
    }

    /**
     * 总账同步为尽力而为（契约 §2.3）：失败只告警，批次仍然有效，ledgerEntryId 留空。
     */
    private void syncLedger(PostingBatch batch) {
        if (!ledgerSyncEnabled) {
            return;
        }
        try {
            String entryId = ledgerClient.recordJournalEntry(new LedgerClient.JournalEntryCommand(
                    LEDGER_SOURCE_TYPE,
                    batch.getBatchId(),
                    ledgerDescription(batch),
                    batch.getCreatedAt(),
                    batch.getCurrency(),
                    batch.getLegs().stream()
                            .map(leg -> new LedgerClient.JournalLineCommand(leg.getLedgerAccountCode(),
                                    leg.getDirection().name(), leg.getAmount()))
                            .toList()));
            if (entryId != null) {
                batch.markLedgerEntry(entryId);
            }
        } catch (RuntimeException ex) {
            log.warn("Ledger sync failed for posting batch {}: {}", batch.getBatchId(), ex.getMessage());
        }
    }

    private static String ledgerDescription(PostingBatch batch) {
        String description = batch.getDescription();
        if (description == null || description.isBlank()) {
            return batch.getTypeCode() + " posting batch " + batch.getBatchId();
        }
        return description;
    }

    private PostingBatchView existingView(PostingBatch existing, String typeCode, String accountId,
                                          BigDecimal amount) {
        boolean sameRequest = existing.getTypeCode().equals(typeCode)
                && existing.getAccountId().equals(accountId)
                && existing.getAmount().compareTo(amount) == 0;
        if (!sameRequest) {
            throw new BusinessRuleException("IDEMPOTENCY_CONFLICT",
                    "Idempotency key " + existing.getIdempotencyKey()
                            + " was already used with a different posting request.");
        }
        return view(existing);
    }

    private PostingBatchView view(PostingBatch batch) {
        String typeName = postingTypes.findType(batch.getTypeCode())
                .map(PostingType::getName)
                .orElse(null);
        return new PostingBatchView(batch, typeName);
    }

    private static String requireIdempotencyKey(String idempotencyKey) {
        String key = trimToNull(idempotencyKey);
        if (key == null) {
            throw new InvalidRequestException("IDEMPOTENCY_KEY_REQUIRED", "Idempotency key must be supplied.");
        }
        return key;
    }

    private static BigDecimal requireAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0 || amount.scale() > 2) {
            throw new BusinessRuleException("INVALID_POSTING_AMOUNT",
                    "Posting amount must be greater than zero with at most 2 decimal places.");
        }
        return amount.setScale(2);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
