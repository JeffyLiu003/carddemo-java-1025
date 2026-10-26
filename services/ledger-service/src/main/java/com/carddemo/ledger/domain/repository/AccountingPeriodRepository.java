package com.carddemo.ledger.domain.repository;

import com.carddemo.ledger.domain.model.AccountingPeriod;

import java.util.List;
import java.util.Optional;

/**
 * 会计期间仓储端口。
 */
public interface AccountingPeriodRepository {

    List<AccountingPeriod> findAllPeriods();

    Optional<AccountingPeriod> findByPeriod(String period);

    AccountingPeriod savePeriod(AccountingPeriod period);
}
