package com.carddemo.account.application;

import com.carddemo.account.domain.exception.NotFoundException;
import com.carddemo.account.domain.model.Account;
import com.carddemo.account.domain.model.AccountDetails;
import com.carddemo.account.domain.repository.AccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * 账户用例服务。对应遗留程序 COACTVWC（查询）、COACTUPC（维护）、
 * CBTRN02C（记账）、COBIL00C（还款）、CBACT04C（利息入账）。
 */
@Service
@Transactional
public class AccountApplicationService {

    private final AccountRepository accounts;

    public AccountApplicationService(AccountRepository accounts) {
        this.accounts = accounts;
    }

    @Transactional(readOnly = true)
    public Account getById(String accountId) {
        return accounts.findByAccountId(accountId)
                .orElseThrow(() -> new NotFoundException("ACCOUNT_NOT_FOUND",
                        "Account " + accountId + " not found"));
    }

    @Transactional(readOnly = true)
    public List<Account> findAll() {
        return accounts.findAllAccounts();
    }

    public Account update(String accountId, AccountDetails details) {
        Account account = getById(accountId);
        account.applyDetails(details);
        return accounts.saveAccount(account);
    }

    public Account applyPosting(String accountId, BigDecimal amount) {
        Account account = getById(accountId);
        account.applyPosting(amount);
        return accounts.saveAccount(account);
    }

    public Account applyBillPayment(String accountId, BigDecimal amount) {
        Account account = getById(accountId);
        account.applyBillPayment(amount);
        return accounts.saveAccount(account);
    }

    public Account accrueInterest(String accountId, BigDecimal amount) {
        Account account = getById(accountId);
        account.accrueInterest(amount);
        return accounts.saveAccount(account);
    }
}
