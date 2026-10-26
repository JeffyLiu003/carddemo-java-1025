package com.carddemo.account.application;

import com.carddemo.account.domain.exception.BusinessRuleException;
import com.carddemo.account.domain.exception.NotFoundException;
import com.carddemo.account.domain.model.Account;
import com.carddemo.account.domain.model.AccountDetails;
import com.carddemo.account.domain.model.AccountProduct;
import com.carddemo.account.domain.repository.AccountProductRepository;
import com.carddemo.account.domain.repository.AccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 账户用例服务。对应遗留程序 COACTVWC（查询）、COACTUPC（维护）、
 * CBTRN02C（记账）、COBIL00C（还款）、CBACT04C（利息入账）。
 */
@Service
@Transactional
public class AccountApplicationService {

    private final AccountRepository accounts;
    private final AccountProductRepository products;

    public AccountApplicationService(AccountRepository accounts, AccountProductRepository products) {
        this.accounts = accounts;
        this.products = products;
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
        AccountProduct product = products.findByProductCode(account.getProductCode())
                .orElseThrow(() -> new NotFoundException("PRODUCT_NOT_FOUND",
                        "Account product " + account.getProductCode() + " not found"));
        if (!product.isActive()) {
            throw new BusinessRuleException("PRODUCT_INACTIVE",
                    "Account product " + product.getProductCode() + " is inactive.");
        }
        if (details.creditLimit() != null
                && details.creditLimit().compareTo(product.getMaxCreditLimit()) > 0) {
            throw new BusinessRuleException("CREDIT_LIMIT_EXCEEDS_PRODUCT_MAX",
                    "Credit limit exceeds product maximum " + product.getMaxCreditLimit() + ".");
        }
        if (details.cashCreditLimit() != null
                && details.cashCreditLimit().compareTo(product.getMaxCashLimit()) > 0) {
            throw new BusinessRuleException("CASH_LIMIT_EXCEEDS_PRODUCT_MAX",
                    "Cash credit limit exceeds product maximum " + product.getMaxCashLimit() + ".");
        }
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
