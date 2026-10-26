package com.carddemo.limit.application;

import com.carddemo.limit.domain.exception.BusinessRuleException;
import com.carddemo.limit.domain.exception.InvalidRequestException;
import com.carddemo.limit.domain.exception.NotFoundException;
import com.carddemo.limit.domain.model.LimitDefinition;
import com.carddemo.limit.domain.model.LimitType;
import com.carddemo.limit.domain.model.ScopeType;
import com.carddemo.limit.domain.repository.PageResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 限额用例：真实 H2 + Flyway，事务回滚保证用例间数据隔离。
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class LimitApplicationServiceTest {

    @Autowired
    private LimitApplicationService limits;

    private static String uniqueScopeId() {
        return "acct-" + UUID.randomUUID();
    }

    private static CreateLimitCommand command(String scopeId, String amount) {
        return new CreateLimitCommand(ScopeType.ACCOUNT, scopeId, LimitType.PURCHASE, "USD",
                new BigDecimal(amount), true);
    }

    @Test
    void createsAndReadsBackLimit() {
        LimitDefinition created = limits.create(command(uniqueScopeId(), "1000.00"));

        assertThat(created.getLimitId()).isNotBlank();
        assertThat(created.getAmount()).isEqualByComparingTo("1000.00");
        assertThat(created.getUsedAmount()).isEqualByComparingTo("0.00");

        LimitDefinition read = limits.get(created.getLimitId());
        assertThat(read.getCurrency()).isEqualTo("USD");
        assertThat(read.getVersion()).isZero();
    }

    @Test
    void listsLimitsByScope() {
        String accountId = uniqueScopeId();
        limits.create(command(accountId, "500.00"));
        limits.create(new CreateLimitCommand(ScopeType.ACCOUNT, accountId, LimitType.CASH,
                "USD", new BigDecimal("200.00"), true));
        limits.create(new CreateLimitCommand(ScopeType.CUSTOMER, "000000001", LimitType.PURCHASE,
                "USD", new BigDecimal("3000.00"), true));

        PageResult<LimitDefinition> forAccount = limits.list(ScopeType.ACCOUNT, accountId, 0, 20);
        assertThat(forAccount.total()).isEqualTo(2);
        assertThat(forAccount.items()).extracting(LimitDefinition::getLimitType)
                .containsExactly(LimitType.CASH, LimitType.PURCHASE);

        PageResult<LimitDefinition> forCustomer = limits.list(ScopeType.CUSTOMER, "000000001", 0, 20);
        assertThat(forCustomer.total()).isEqualTo(1);
        assertThat(forCustomer.items().get(0).getScopeType()).isEqualTo(ScopeType.CUSTOMER);
    }

    @Test
    void duplicateDefinitionConflicts() {
        String accountId = uniqueScopeId();
        limits.create(command(accountId, "1000.00"));

        assertThatThrownBy(() -> limits.create(command(accountId, "2000.00")))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code").isEqualTo("LIMIT_ALREADY_DEFINED");
    }

    @Test
    void adjustRejectsAmountBelowUsed() {
        LimitDefinition created = limits.create(command(uniqueScopeId(), "1000.00"));
        limits.reserve(created.getLimitId(), new BigDecimal("400.00"));

        assertThatThrownBy(() -> limits.adjust(created.getLimitId(), new BigDecimal("399.99")))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code").isEqualTo("LIMIT_BELOW_USED");

        LimitDefinition adjusted = limits.adjust(created.getLimitId(), new BigDecimal("800.00"));
        assertThat(adjusted.getAmount()).isEqualByComparingTo("800.00");
        assertThat(adjusted.remainingAmount()).isEqualByComparingTo("400.00");
    }

    @Test
    void reserveAndReleaseMoveUsageWithinLimit() {
        LimitDefinition created = limits.create(command(uniqueScopeId(), "300.00"));

        LimitDefinition reserved = limits.reserve(created.getLimitId(), new BigDecimal("250.00"));
        assertThat(reserved.getUsedAmount()).isEqualByComparingTo("250.00");

        assertThatThrownBy(() -> limits.reserve(created.getLimitId(), new BigDecimal("50.01")))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code").isEqualTo("LIMIT_EXCEEDED");

        LimitDefinition released = limits.release(created.getLimitId(), new BigDecimal("250.00"));
        assertThat(released.getUsedAmount()).isEqualByComparingTo("0.00");

        assertThatThrownBy(() -> limits.release(created.getLimitId(), new BigDecimal("0.01")))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code").isEqualTo("INVALID_LIMIT_RELEASE");
    }

    @Test
    void checkReflectsRemainingUsage() {
        String accountId = uniqueScopeId();
        LimitDefinition created = limits.create(command(accountId, "1000.00"));
        limits.reserve(created.getLimitId(), new BigDecimal("250.00"));

        LimitCheckView allowed = limits.check(ScopeType.ACCOUNT, accountId, LimitType.PURCHASE,
                "usd", new BigDecimal("750.00"));
        assertThat(allowed.allowed()).isTrue();
        assertThat(allowed.limitId()).isEqualTo(created.getLimitId());
        assertThat(allowed.usedAmount()).isEqualByComparingTo("250.00");
        assertThat(allowed.remainingAmount()).isEqualByComparingTo("750.00");

        LimitCheckView rejected = limits.check(ScopeType.ACCOUNT, accountId, LimitType.PURCHASE,
                "USD", new BigDecimal("750.01"));
        assertThat(rejected.allowed()).isFalse();
        assertThat(rejected.remainingAmount()).isEqualByComparingTo("750.00");
    }

    @Test
    void checkWithoutMatchingLimitIsNotAllowed() {
        LimitCheckView view = limits.check(ScopeType.ACCOUNT, uniqueScopeId(), LimitType.TRANSFER,
                "USD", new BigDecimal("10.00"));

        assertThat(view.allowed()).isFalse();
        assertThat(view.limitId()).isNull();
        assertThat(view.usedAmount()).isEqualByComparingTo("0.00");
        assertThat(view.remainingAmount()).isEqualByComparingTo("0.00");
    }

    @Test
    void checkRejectsNonPositiveAmount() {
        assertThatThrownBy(() -> limits.check(ScopeType.ACCOUNT, uniqueScopeId(), LimitType.PURCHASE,
                "USD", new BigDecimal("0.00")))
                .isInstanceOf(InvalidRequestException.class)
                .extracting("code").isEqualTo("INVALID_AMOUNT");
    }

    @Test
    void unknownLimitIsNotFound() {
        assertThatThrownBy(() -> limits.get("missing-limit"))
                .isInstanceOf(NotFoundException.class)
                .extracting("code").isEqualTo("LIMIT_NOT_FOUND");
        assertThatThrownBy(() -> limits.reserve("missing-limit", new BigDecimal("1.00")))
                .isInstanceOf(NotFoundException.class)
                .extracting("code").isEqualTo("LIMIT_NOT_FOUND");
    }
}
