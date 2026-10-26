package com.carddemo.account.domain.repository;

import com.carddemo.account.domain.model.AccountHold;
import com.carddemo.account.domain.model.HoldStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 冻结仓储端口。
 */
public interface AccountHoldRepository {

    AccountHold save(AccountHold hold);

    Optional<AccountHold> findByHoldIdAndAccountId(String holdId, String accountId);

    Optional<AccountHold> findByAccountIdAndReferenceId(String accountId, String referenceId);

    List<AccountHold> findByAccountIdOrderByCreatedAtDesc(String accountId);

    List<AccountHold> findByAccountIdAndStatusOrderByCreatedAtDesc(String accountId, HoldStatus status);

    /**
     * AH-07：只统计未过期且 ACTIVE 的冻结。
     */
    BigDecimal sumActiveAmount(String accountId, LocalDateTime now);
}
