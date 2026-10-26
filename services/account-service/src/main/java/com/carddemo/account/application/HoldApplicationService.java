package com.carddemo.account.application;

import com.carddemo.account.domain.exception.BusinessRuleException;
import com.carddemo.account.domain.exception.NotFoundException;
import com.carddemo.account.domain.model.Account;
import com.carddemo.account.domain.model.AccountHold;
import com.carddemo.account.domain.model.HoldStatus;
import com.carddemo.account.domain.repository.AccountHoldRepository;
import com.carddemo.account.domain.repository.AccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * 资金冻结用例：创建、释放、查询与可用额度。
 */
@Service
@Transactional
public class HoldApplicationService {

    private final AccountRepository accounts;
    private final AccountHoldRepository holds;

    public HoldApplicationService(AccountRepository accounts, AccountHoldRepository holds) {
        this.accounts = accounts;
        this.holds = holds;
    }

    /**
     * AH-01..AH-05、AH-08：创建冻结；同一 referenceId 幂等，账户级悲观锁防止并发超冻。
     */
    public AccountHold create(String accountId, BigDecimal amount, String referenceType,
                              String referenceId, LocalDateTime expiresAt) {
        if (amount == null || amount.signum() <= 0) {
            throw new BusinessRuleException("INVALID_HOLD_AMOUNT",
                    "Hold amount must be greater than zero.");
        }
        String type = normalizeType(referenceType);
        String reference = normalizeReference(referenceId);
        if (type == null || reference == null) {
            throw new BusinessRuleException("INVALID_HOLD_REFERENCE",
                    "Reference type and reference id must be supplied.");
        }

        Account account = lockAccount(accountId);
        LocalDateTime now = LocalDateTime.now();

        Optional<AccountHold> existing = holds.findByAccountIdAndReferenceId(accountId, reference);
        if (existing.isPresent()) {
            AccountHold hold = existing.get();
            if (hold.getAmount().compareTo(amount) != 0) {
                throw new BusinessRuleException("HOLD_REFERENCE_CONFLICT",
                        "Reference " + reference + " already used with a different amount.");
            }
            if (hold.getStatus() != HoldStatus.ACTIVE) {
                throw new BusinessRuleException("HOLD_REFERENCE_REUSED",
                        "Reference " + reference + " belongs to a " + hold.getStatus() + " hold.");
            }
            return hold;
        }

        BigDecimal activeHolds = holds.sumActiveAmount(accountId, now);
        account.assertCanReserve(amount, activeHolds);

        AccountHold hold = AccountHold.create(UUID.randomUUID().toString(), accountId, amount,
                type, reference, now, expiresAt);
        return holds.save(hold);
    }

    /**
     * AH-06：释放冻结；已释放的记录幂等返回。
     */
    public AccountHold release(String accountId, String holdId) {
        lockAccount(accountId);
        AccountHold hold = holds.findByHoldIdAndAccountId(holdId, accountId)
                .orElseThrow(() -> new NotFoundException("HOLD_NOT_FOUND",
                        "Hold " + holdId + " not found for account " + accountId));
        if (hold.getStatus() == HoldStatus.RELEASED) {
            return hold;
        }
        hold.release(LocalDateTime.now());
        return holds.save(hold);
    }

    @Transactional(readOnly = true)
    public List<AccountHold> list(String accountId, HoldStatus status) {
        requireAccount(accountId);
        return status == null
                ? holds.findByAccountIdOrderByCreatedAtDesc(accountId)
                : holds.findByAccountIdAndStatusOrderByCreatedAtDesc(accountId, status);
    }

    @Transactional(readOnly = true)
    public CreditPositionView creditPosition(String accountId) {
        Account account = requireAccount(accountId);
        BigDecimal activeHolds = holds.sumActiveAmount(accountId, LocalDateTime.now());
        return new CreditPositionView(
                account.getAccountId(),
                account.getProductCode(),
                account.getCreditLimit(),
                account.getCurrentBalance(),
                activeHolds,
                account.availableCredit(activeHolds));
    }

    private Account lockAccount(String accountId) {
        return accounts.lockByAccountId(accountId)
                .orElseThrow(() -> new NotFoundException("ACCOUNT_NOT_FOUND",
                        "Account " + accountId + " not found"));
    }

    private Account requireAccount(String accountId) {
        return accounts.findByAccountId(accountId)
                .orElseThrow(() -> new NotFoundException("ACCOUNT_NOT_FOUND",
                        "Account " + accountId + " not found"));
    }

    private static String normalizeType(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed.toUpperCase(Locale.ROOT);
    }

    private static String normalizeReference(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
