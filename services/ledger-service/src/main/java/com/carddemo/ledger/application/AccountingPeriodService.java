package com.carddemo.ledger.application;

import com.carddemo.ledger.domain.exception.NotFoundException;
import com.carddemo.ledger.domain.model.AccountingPeriod;
import com.carddemo.ledger.domain.repository.AccountingPeriodRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 会计期间用例：列表与关账。
 */
@Service
@Transactional
public class AccountingPeriodService {

    private final AccountingPeriodRepository periods;

    public AccountingPeriodService(AccountingPeriodRepository periods) {
        this.periods = periods;
    }

    @Transactional(readOnly = true)
    public List<AccountingPeriod> findAll() {
        return periods.findAllPeriods();
    }

    /**
     * 关账：期间不存在 404；已关账 409。
     */
    public AccountingPeriod close(String period) {
        String key = AccountingPeriod.parsePeriod(period).toString();
        AccountingPeriod accountingPeriod = periods.findByPeriod(key)
                .orElseThrow(() -> new NotFoundException("PERIOD_NOT_FOUND",
                        "Accounting period " + key + " not found"));
        accountingPeriod.close(LocalDateTime.now());
        return periods.savePeriod(accountingPeriod);
    }
}
