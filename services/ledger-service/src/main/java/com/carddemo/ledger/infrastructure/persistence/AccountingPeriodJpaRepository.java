package com.carddemo.ledger.infrastructure.persistence;

import com.carddemo.ledger.domain.model.AccountingPeriod;
import com.carddemo.ledger.domain.repository.AccountingPeriodRepository;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AccountingPeriodJpaRepository
        extends JpaRepository<AccountingPeriod, String>, AccountingPeriodRepository {

    @Override
    default List<AccountingPeriod> findAllPeriods() {
        return findAllByOrderByPeriodAsc();
    }

    @Override
    default Optional<AccountingPeriod> findByPeriod(String period) {
        return findById(period);
    }

    @Override
    default AccountingPeriod savePeriod(AccountingPeriod period) {
        return save(period);
    }

    List<AccountingPeriod> findAllByOrderByPeriodAsc();
}
