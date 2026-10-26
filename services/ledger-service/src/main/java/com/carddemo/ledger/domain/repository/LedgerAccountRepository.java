package com.carddemo.ledger.domain.repository;

import com.carddemo.ledger.domain.model.LedgerAccount;

import java.util.List;
import java.util.Optional;

/**
 * 科目表仓储端口。
 */
public interface LedgerAccountRepository {

    List<LedgerAccount> findAllAccounts();

    Optional<LedgerAccount> findByAccountCode(String accountCode);
}
