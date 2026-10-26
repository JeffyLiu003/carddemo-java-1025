package com.carddemo.account.application;

import com.carddemo.account.domain.exception.BusinessRuleException;
import com.carddemo.account.domain.exception.NotFoundException;
import com.carddemo.account.domain.model.Account;
import com.carddemo.account.domain.model.AccountCredit;
import com.carddemo.account.domain.model.AccountDetails;
import com.carddemo.account.domain.model.AccountProduct;
import com.carddemo.account.domain.repository.AccountCreditRepository;
import com.carddemo.account.domain.repository.AccountProductRepository;
import com.carddemo.account.domain.repository.AccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * 账户用例服务。对应遗留程序 COACTVWC（查询）、COACTUPC（维护）、
 * CBTRN02C（记账）、COBIL00C（还款）、CBACT04C（利息入账）。
 */
@Service
@Transactional
public class AccountApplicationService {

    private final AccountRepository accounts;
    private final AccountProductRepository products;
    private final AccountCreditRepository credits;

    public AccountApplicationService(AccountRepository accounts, AccountProductRepository products,
                                     AccountCreditRepository credits) {
        this.accounts = accounts;
        this.products = products;
        this.credits = credits;
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

    /**
     * 通用账户贷记（如营销返现冲减欠款）：creditId 幂等，重复调用不再冲减（docs/design/17 §3.2）。
     * 账户行加悲观锁串行化同一账户的余额变更；余额允许为负（溢缴）。
     */
    public Account credit(String accountId, String creditId, String sourceType, BigDecimal amount,
                          String description) {
        Account account = accounts.lockByAccountId(accountId)
                .orElseThrow(() -> new NotFoundException("ACCOUNT_NOT_FOUND",
                        "Account " + accountId + " not found"));
        String id = requireCreditId(creditId);
        Optional<AccountCredit> existing = credits.findByCreditId(id);
        if (existing.isPresent()) {
            AccountCredit credit = existing.get();
            if (!credit.getAccountId().equals(accountId)) {
                throw new BusinessRuleException("CREDIT_ID_CONFLICT",
                        "Credit " + id + " was already applied to account " + credit.getAccountId() + ".");
            }
            return account;
        }

        AccountCredit credit = AccountCredit.create(id, accountId, normalizeType(sourceType),
                amount, trimToNull(description), LocalDateTime.now());
        account.applyCredit(amount);
        credits.save(credit);
        return accounts.saveAccount(account);
    }

    private static String requireCreditId(String value) {
        String trimmed = trimToNull(value);
        if (trimmed == null || trimmed.length() > 36) {
            throw new BusinessRuleException("INVALID_CREDIT_ID", "Credit id must be 1 to 36 characters.");
        }
        return trimmed;
    }

    private static String normalizeType(String value) {
        String trimmed = trimToNull(value);
        if (trimmed == null || trimmed.length() > 30) {
            throw new BusinessRuleException("INVALID_CREDIT_SOURCE", "Source type must be 1 to 30 characters.");
        }
        return trimmed.toUpperCase(Locale.ROOT);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
