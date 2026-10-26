package com.carddemo.ledger.application;

import com.carddemo.ledger.domain.exception.NotFoundException;
import com.carddemo.ledger.domain.model.LedgerAccount;
import com.carddemo.ledger.domain.repository.LedgerAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 科目表查询。
 */
@Service
@Transactional(readOnly = true)
public class LedgerAccountQueryService {

    private final LedgerAccountRepository accounts;

    public LedgerAccountQueryService(LedgerAccountRepository accounts) {
        this.accounts = accounts;
    }

    public List<LedgerAccount> findAll() {
        return accounts.findAllAccounts();
    }

    public LedgerAccount getByCode(String accountCode) {
        return accounts.findByAccountCode(accountCode)
                .orElseThrow(() -> new NotFoundException("LEDGER_ACCOUNT_NOT_FOUND",
                        "Ledger account " + accountCode + " not found"));
    }
}
