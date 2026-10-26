package com.carddemo.ledger.infrastructure.persistence;

import com.carddemo.ledger.domain.model.LedgerAccount;
import com.carddemo.ledger.domain.repository.LedgerAccountRepository;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LedgerAccountJpaRepository
        extends JpaRepository<LedgerAccount, String>, LedgerAccountRepository {

    @Override
    default List<LedgerAccount> findAllAccounts() {
        return findAllByOrderByAccountCodeAsc();
    }

    @Override
    default Optional<LedgerAccount> findByAccountCode(String accountCode) {
        return findById(accountCode);
    }

    List<LedgerAccount> findAllByOrderByAccountCodeAsc();
}
