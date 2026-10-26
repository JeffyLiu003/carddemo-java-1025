package com.carddemo.billing.application;

import com.carddemo.billing.domain.exception.InvalidRequestException;
import com.carddemo.billing.domain.exception.NotFoundException;
import com.carddemo.billing.domain.exception.RemoteServiceException;
import com.carddemo.billing.domain.model.AccountCycleTotals;
import com.carddemo.billing.domain.model.AccountPosition;
import com.carddemo.billing.domain.model.AccountProductSnapshot;
import com.carddemo.billing.domain.model.Statement;
import com.carddemo.billing.domain.model.StatementStatus;
import com.carddemo.billing.domain.repository.PageResult;
import com.carddemo.billing.infrastructure.client.AccountClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 账单用例：真实 H2 + Flyway，account-service 用 @MockBean 替换；事务回滚保证用例间数据隔离。
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class StatementServiceTest {

    private static final String ACCOUNT_ID = "00000000001";
    private static final String PRODUCT_CODE = "CARD_BASIC";
    private static final String PERIOD = "2026-10";

    @Autowired
    private StatementService statements;

    @MockBean
    private AccountClient accountClient;

    private void stubAccount(String accountId, String balance, String cycleCredit, String cycleDebit,
                             String apr) {
        when(accountClient.creditPosition(accountId)).thenReturn(new AccountPosition(accountId, PRODUCT_CODE,
                new BigDecimal("5000.00"), new BigDecimal(balance), new BigDecimal("3060.00")));
        when(accountClient.cycleTotals(accountId)).thenReturn(new AccountCycleTotals(accountId, PRODUCT_CODE,
                new BigDecimal(cycleCredit), new BigDecimal(cycleDebit)));
        when(accountClient.findProduct(PRODUCT_CODE))
                .thenReturn(new AccountProductSnapshot(PRODUCT_CODE, new BigDecimal(apr)));
    }

    private Statement generate(String accountId, String period) {
        return statements.generate(new GenerateStatementCommand(accountId, period));
    }

    @Test
    void generatesStatementFromAccountPositionAndProductApr() {
        stubAccount(ACCOUNT_ID, "1940.00", "200.00", "-300.00", "0.1899");

        Statement statement = generate(ACCOUNT_ID, PERIOD);

        assertThat(statement.getStatementId()).isNotBlank();
        assertThat(statement.getAccountId()).isEqualTo(ACCOUNT_ID);
        assertThat(statement.getPeriod()).isEqualTo(PERIOD);
        assertThat(statement.getClosingBalance()).isEqualByComparingTo("1940.00");
        assertThat(statement.getOpeningBalance()).isEqualByComparingTo("2040.00");
        assertThat(statement.getPurchases()).isEqualByComparingTo("200.00");
        assertThat(statement.getPayments()).isEqualByComparingTo("300.00");
        assertThat(statement.getFees()).isEqualByComparingTo("0.00");
        assertThat(statement.getInterest()).isEqualByComparingTo("30.70");
        assertThat(statement.getMinimumPayment()).isEqualByComparingTo("58.20");
        assertThat(statement.getCycleDate()).isEqualTo(LocalDate.of(2026, 10, 31));
        assertThat(statement.getDueDate()).isEqualTo(LocalDate.of(2026, 11, 25));
        assertThat(statement.getStatus()).isEqualTo(StatementStatus.GENERATED);
        assertThat(statements.get(statement.getStatementId()).getStatementId())
                .isEqualTo(statement.getStatementId());
    }

    @Test
    void generationIsIdempotentPerAccountAndPeriod() {
        stubAccount(ACCOUNT_ID, "1940.00", "200.00", "-300.00", "0.1899");

        Statement first = generate(ACCOUNT_ID, PERIOD);
        Statement second = generate(ACCOUNT_ID, PERIOD);

        assertThat(second.getStatementId()).isEqualTo(first.getStatementId());
        assertThat(statements.list(ACCOUNT_ID, 0, 10).total()).isEqualTo(1);
        verify(accountClient, times(1)).creditPosition(ACCOUNT_ID);
        verify(accountClient, times(1)).findProduct(PRODUCT_CODE);
    }

    @Test
    void zeroBalanceStatementIsPaidWithoutCallingAccount() {
        stubAccount(ACCOUNT_ID, "0.00", "0.00", "0.00", "0.1899");
        Statement statement = generate(ACCOUNT_ID, PERIOD);

        Statement paid = statements.pay(statement.getStatementId());

        assertThat(paid.getStatus()).isEqualTo(StatementStatus.PAID);
        assertThat(paid.getPaidAt()).isNotNull();
        verify(accountClient, never()).payBill(any(), any());
    }

    @Test
    void payingStatementCallsAccountForClosingBalance() {
        stubAccount(ACCOUNT_ID, "1940.00", "200.00", "-300.00", "0.1899");
        Statement statement = generate(ACCOUNT_ID, PERIOD);

        Statement paid = statements.pay(statement.getStatementId());

        assertThat(paid.getStatus()).isEqualTo(StatementStatus.PAID);
        assertThat(paid.getPaidAt()).isNotNull();
        verify(accountClient).payBill(ACCOUNT_ID, new BigDecimal("1940.00"));
    }

    @Test
    void repeatedPaymentDoesNotPayTwice() {
        stubAccount(ACCOUNT_ID, "1940.00", "0.00", "0.00", "0.1899");
        Statement statement = generate(ACCOUNT_ID, PERIOD);

        Statement first = statements.pay(statement.getStatementId());
        Statement second = statements.pay(statement.getStatementId());

        assertThat(second.getStatus()).isEqualTo(StatementStatus.PAID);
        assertThat(second.getPaidAt()).isEqualTo(first.getPaidAt());
        verify(accountClient, times(1)).payBill(ACCOUNT_ID, new BigDecimal("1940.00"));
    }

    @Test
    void rejectsPeriodNotFormattedAsYearMonth() {
        assertThatThrownBy(() -> generate(ACCOUNT_ID, "2026-13"))
                .isInstanceOf(InvalidRequestException.class)
                .extracting("code").isEqualTo("INVALID_PERIOD");
        assertThatThrownBy(() -> generate(ACCOUNT_ID, "2026/10"))
                .isInstanceOf(InvalidRequestException.class)
                .extracting("code").isEqualTo("INVALID_PERIOD");
        assertThatThrownBy(() -> generate(ACCOUNT_ID, null))
                .isInstanceOf(InvalidRequestException.class)
                .extracting("code").isEqualTo("INVALID_PERIOD");
    }

    @Test
    void propagatesUnknownAccountFromAccountService() {
        when(accountClient.creditPosition(ACCOUNT_ID))
                .thenThrow(new NotFoundException("ACCOUNT_NOT_FOUND", "Account " + ACCOUNT_ID + " not found"));

        assertThatThrownBy(() -> generate(ACCOUNT_ID, PERIOD))
                .isInstanceOf(NotFoundException.class)
                .extracting("code").isEqualTo("ACCOUNT_NOT_FOUND");
    }

    @Test
    void propagatesUnknownProductFromAccountService() {
        when(accountClient.creditPosition(ACCOUNT_ID)).thenReturn(new AccountPosition(ACCOUNT_ID, PRODUCT_CODE,
                new BigDecimal("5000.00"), new BigDecimal("1940.00"), new BigDecimal("3060.00")));
        when(accountClient.cycleTotals(ACCOUNT_ID)).thenReturn(new AccountCycleTotals(ACCOUNT_ID, PRODUCT_CODE,
                new BigDecimal("0.00"), new BigDecimal("0.00")));
        when(accountClient.findProduct(PRODUCT_CODE))
                .thenThrow(new NotFoundException("PRODUCT_NOT_FOUND", "Account product not found"));

        assertThatThrownBy(() -> generate(ACCOUNT_ID, PERIOD))
                .isInstanceOf(NotFoundException.class)
                .extracting("code").isEqualTo("PRODUCT_NOT_FOUND");
    }

    @Test
    void propagatesDownstreamFailure() {
        when(accountClient.creditPosition(ACCOUNT_ID))
                .thenThrow(new RemoteServiceException("account-service is unavailable"));

        assertThatThrownBy(() -> generate(ACCOUNT_ID, PERIOD))
                .isInstanceOf(RemoteServiceException.class)
                .hasMessageContaining("unavailable");
    }

    @Test
    void rejectsUnknownStatement() {
        assertThatThrownBy(() -> statements.get("missing"))
                .isInstanceOf(NotFoundException.class)
                .extracting("code").isEqualTo("STATEMENT_NOT_FOUND");
        assertThatThrownBy(() -> statements.pay("missing"))
                .isInstanceOf(NotFoundException.class)
                .extracting("code").isEqualTo("STATEMENT_NOT_FOUND");
    }

    @Test
    void listsStatementsByAccountWithPagination() {
        stubAccount(ACCOUNT_ID, "1940.00", "0.00", "0.00", "0.1899");
        stubAccount("00000000002", "500.00", "0.00", "0.00", "0.1899");

        generate(ACCOUNT_ID, "2026-09");
        generate(ACCOUNT_ID, "2026-10");
        generate("00000000002", "2026-10");

        PageResult<Statement> page = statements.list(ACCOUNT_ID, 0, 1);

        assertThat(page.total()).isEqualTo(2);
        assertThat(page.items()).hasSize(1);
        assertThat(page.items().get(0).getPeriod()).isEqualTo("2026-10");
    }
}
