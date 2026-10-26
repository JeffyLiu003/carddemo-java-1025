package com.carddemo.account.domain.repository;

import com.carddemo.account.domain.model.AccountCredit;

import java.util.Optional;

/**
 * 账户贷记仓储端口。实现见 infrastructure.persistence.AccountCreditJpaRepository。
 */
public interface AccountCreditRepository {

    AccountCredit save(AccountCredit credit);

    /**
     * 幂等键查找：同一 creditId 只允许冲减一次。
     */
    Optional<AccountCredit> findByCreditId(String creditId);
}
