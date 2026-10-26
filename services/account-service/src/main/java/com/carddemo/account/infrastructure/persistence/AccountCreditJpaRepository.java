package com.carddemo.account.infrastructure.persistence;

import com.carddemo.account.domain.model.AccountCredit;
import com.carddemo.account.domain.repository.AccountCreditRepository;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AccountCreditJpaRepository
        extends JpaRepository<AccountCredit, String>, AccountCreditRepository {

    @Override
    Optional<AccountCredit> findByCreditId(String creditId);
}
