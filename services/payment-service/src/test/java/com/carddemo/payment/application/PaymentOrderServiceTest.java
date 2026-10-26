package com.carddemo.payment.application;

import com.carddemo.payment.domain.exception.BusinessRuleException;
import com.carddemo.payment.domain.exception.InvalidRequestException;
import com.carddemo.payment.domain.exception.NotFoundException;
import com.carddemo.payment.domain.exception.RemoteServiceException;
import com.carddemo.payment.domain.model.AccountSnapshot;
import com.carddemo.payment.domain.model.PaymentOrder;
import com.carddemo.payment.domain.model.PaymentStatus;
import com.carddemo.payment.domain.model.PaymentType;
import com.carddemo.payment.domain.repository.PageResult;
import com.carddemo.payment.domain.repository.PaymentOrderRepository;
import com.carddemo.payment.infrastructure.client.AccountClient;
import com.carddemo.payment.infrastructure.client.TransactionClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 支付指令用例：account-service / transaction-service 用 Mockito 替换，本地支付库为真实 H2 + Flyway。
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PaymentOrderServiceTest {

    private static final String ACCOUNT_ID = "00000000006";

    @Autowired
    private PaymentOrderService paymentOrderService;

    @Autowired
    private PaymentOrderRepository paymentOrders;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private AccountClient accountClient;

    @MockBean
    private TransactionClient transactionClient;

    @BeforeEach
    void stubDownstream() {
        when(accountClient.findAccount(ACCOUNT_ID))
                .thenReturn(Optional.of(new AccountSnapshot(ACCOUNT_ID, "Y")));
        when(transactionClient.postPayment(anyString(), anyString(), any(BigDecimal.class), anyString(),
                anyString())).thenReturn("BATCH-0001");
    }

    @Test
    void createScreensPaymentWhenCreditorIsAllowed() {
        PaymentOrder order = paymentOrderService.create(command("pay-0001", "Acme Utility", "120.00"));

        assertThat(order.getStatus()).isEqualTo(PaymentStatus.SCREENED);
        assertThat(order.getPaymentType()).isEqualTo(PaymentType.ACH);
        assertThat(order.getAmount()).isEqualByComparingTo("120.00");
        assertThat(order.getCurrency()).isEqualTo("USD");
        assertThat(order.getDebtorAccountId()).isEqualTo(ACCOUNT_ID);
        assertThat(order.getRejectReason()).isNull();
        assertThat(order.getCreatedAt()).isNotNull();
        assertThat(order.getUpdatedAt()).isNotNull();
        assertThat(paymentOrders.findByPaymentId(order.getPaymentId())).isPresent();
        verify(accountClient).findAccount(ACCOUNT_ID);
    }

    @Test
    void createRejectsPaymentWithoutCreditorName() {
        PaymentOrder order = paymentOrderService.create(command("pay-0002", "   ", "120.00"));

        assertThat(order.getStatus()).isEqualTo(PaymentStatus.REJECTED);
        assertThat(order.getRejectReason()).isEqualTo("CREDITOR_NAME_MISSING");
    }

    @Test
    void createRejectsPaymentOnDenyList() {
        for (String creditorName : List.of("ACME FRAUD LTD", "Blocked Merchant")) {
            PaymentOrder order = paymentOrderService
                    .create(command("pay-0003-" + creditorName, creditorName, "120.00"));

            assertThat(order.getStatus()).isEqualTo(PaymentStatus.REJECTED);
            assertThat(order.getRejectReason()).isEqualTo("CREDITOR_BLOCKED");
        }
    }

    @Test
    void repeatedIdempotencyKeyReturnsOriginalOrder() {
        PaymentOrder first = paymentOrderService.create(command("pay-0004", "Acme Utility", "120.00"));
        PaymentOrder second = paymentOrderService.create(command("pay-0004", "Acme Utility", "120.00"));

        assertThat(second.getPaymentId()).isEqualTo(first.getPaymentId());
        assertThat(second.getIdempotencyKey()).isEqualTo("pay-0004");
        verify(accountClient, times(1)).findAccount(ACCOUNT_ID);
    }

    @Test
    void idempotencyKeyWithDifferentRequestConflicts() {
        paymentOrderService.create(command("pay-0005", "Acme Utility", "120.00"));

        assertThatThrownBy(() -> paymentOrderService.create(command("pay-0005", "Acme Utility", "130.00")))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("IDEMPOTENCY_CONFLICT"));
        assertThatThrownBy(() -> paymentOrderService.create(command("pay-0005", "Other Utility", "120.00")))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("IDEMPOTENCY_CONFLICT"));
    }

    @Test
    void acceptMovesScreenedOrderToAccepted() {
        PaymentOrder order = paymentOrderService.create(command("pay-0006", "Acme Utility", "120.00"));

        PaymentOrder accepted = paymentOrderService.accept(order.getPaymentId());

        assertThat(accepted.getStatus()).isEqualTo(PaymentStatus.ACCEPTED);
        assertThat(paymentOrders.findByPaymentId(order.getPaymentId())).get()
                .extracting(PaymentOrder::getStatus).isEqualTo(PaymentStatus.ACCEPTED);
    }

    @Test
    void repeatedAcceptIsIdempotent() {
        PaymentOrder order = paymentOrderService.create(command("pay-0007", "Acme Utility", "120.00"));

        paymentOrderService.accept(order.getPaymentId());
        PaymentOrder second = paymentOrderService.accept(order.getPaymentId());

        assertThat(second.getStatus()).isEqualTo(PaymentStatus.ACCEPTED);
        assertThat(storedStatus(order.getPaymentId())).isEqualTo("ACCEPTED");
    }

    @Test
    void acceptRequiresScreenedStatus() {
        PaymentOrder rejected = paymentOrderService.create(command("pay-0008", "Acme Fraud Ltd", "120.00"));

        assertThatThrownBy(() -> paymentOrderService.accept(rejected.getPaymentId()))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("PAYMENT_NOT_SCREENED"));

        PaymentOrder settled = settledOrder("pay-0009");
        assertThatThrownBy(() -> paymentOrderService.accept(settled.getPaymentId()))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("PAYMENT_NOT_SCREENED"));
    }

    @Test
    void settlePostsPaymentBatchAndMarksSettled() {
        PaymentOrder order = acceptedOrder("pay-0010");

        PaymentOrder settled = paymentOrderService.settle(order.getPaymentId());

        assertThat(settled.getStatus()).isEqualTo(PaymentStatus.SETTLED);
        assertThat(settled.getTransactionBatchId()).isEqualTo("BATCH-0001");
        assertThat(settled.getSettledAt()).isNotNull();
        verify(transactionClient).postPayment(eq("payment:" + order.getPaymentId()), eq(ACCOUNT_ID),
                eq(new BigDecimal("120.00")), eq("USD"), eq("Utility bill"));
    }

    @Test
    void repeatedSettleDoesNotPostTwice() {
        PaymentOrder order = acceptedOrder("pay-0011");

        PaymentOrder first = paymentOrderService.settle(order.getPaymentId());
        PaymentOrder second = paymentOrderService.settle(order.getPaymentId());

        assertThat(second.getTransactionBatchId()).isEqualTo(first.getTransactionBatchId());
        verify(transactionClient, times(1)).postPayment(anyString(), anyString(), any(BigDecimal.class),
                anyString(), anyString());
    }

    @Test
    void settleRequiresAcceptedStatus() {
        PaymentOrder screened = paymentOrderService.create(command("pay-0012", "Acme Utility", "120.00"));

        assertThatThrownBy(() -> paymentOrderService.settle(screened.getPaymentId()))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("PAYMENT_NOT_ACCEPTED"));
        verify(transactionClient, never()).postPayment(anyString(), anyString(), any(BigDecimal.class),
                anyString(), anyString());
    }

    @Test
    void settleLeavesStatusUnchangedWhenTransactionServiceIsDown() {
        PaymentOrder order = acceptedOrder("pay-0013");
        when(transactionClient.postPayment(anyString(), anyString(), any(BigDecimal.class), anyString(),
                anyString())).thenThrow(new RemoteServiceException("transaction-service is unavailable"));

        assertThatThrownBy(() -> paymentOrderService.settle(order.getPaymentId()))
                .isInstanceOf(RemoteServiceException.class);

        assertThat(storedStatus(order.getPaymentId())).isEqualTo("ACCEPTED");
        assertThat(storedBatchId(order.getPaymentId())).isNull();
    }

    @Test
    void returnOfSettledPaymentReversesPostingBatch() {
        PaymentOrder order = settledOrder("pay-0014");

        PaymentOrder returned = paymentOrderService.returnPayment(order.getPaymentId());

        assertThat(returned.getStatus()).isEqualTo(PaymentStatus.RETURNED);
        assertThat(returned.getReturnedAt()).isNotNull();
        verify(transactionClient).reverseBatch("BATCH-0001");
    }

    @Test
    void returnOfAcceptedPaymentDoesNotReverse() {
        PaymentOrder order = acceptedOrder("pay-0015");

        PaymentOrder returned = paymentOrderService.returnPayment(order.getPaymentId());

        assertThat(returned.getStatus()).isEqualTo(PaymentStatus.RETURNED);
        verify(transactionClient, never()).reverseBatch(anyString());
    }

    @Test
    void repeatedReturnIsIdempotent() {
        PaymentOrder order = settledOrder("pay-0016");

        PaymentOrder first = paymentOrderService.returnPayment(order.getPaymentId());
        PaymentOrder second = paymentOrderService.returnPayment(order.getPaymentId());

        assertThat(second.getStatus()).isEqualTo(PaymentStatus.RETURNED);
        assertThat(second.getReturnedAt()).isEqualTo(first.getReturnedAt());
        verify(transactionClient, times(1)).reverseBatch("BATCH-0001");
    }

    @Test
    void returnRequiresAcceptedOrSettledStatus() {
        PaymentOrder screened = paymentOrderService.create(command("pay-0017", "Acme Utility", "120.00"));

        assertThatThrownBy(() -> paymentOrderService.returnPayment(screened.getPaymentId()))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("PAYMENT_NOT_RETURNABLE"));
        verify(transactionClient, never()).reverseBatch(anyString());
    }

    @Test
    void returnLeavesStatusUnchangedWhenReversalFails() {
        PaymentOrder order = settledOrder("pay-0018");
        doThrow(new RemoteServiceException("transaction-service is unavailable"))
                .when(transactionClient).reverseBatch("BATCH-0001");

        assertThatThrownBy(() -> paymentOrderService.returnPayment(order.getPaymentId()))
                .isInstanceOf(RemoteServiceException.class);

        assertThat(storedStatus(order.getPaymentId())).isEqualTo("SETTLED");
    }

    @Test
    void unknownAccountIsRejected() {
        when(accountClient.findAccount("99999999999")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentOrderService.create(new CreatePaymentOrderCommand(
                "pay-0019", "99999999999", "Acme Utility", "1234567890123456", "BOFAUS3N",
                new BigDecimal("120.00"), "USD", "ACH", "INV-2026-10", "Utility bill")))
                .isInstanceOfSatisfying(NotFoundException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("ACCOUNT_NOT_FOUND"));
    }

    @Test
    void inactiveAccountIsRejected() {
        when(accountClient.findAccount(ACCOUNT_ID))
                .thenReturn(Optional.of(new AccountSnapshot(ACCOUNT_ID, "N")));

        assertThatThrownBy(() -> paymentOrderService.create(command("pay-0020", "Acme Utility", "120.00")))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("ACCOUNT_INACTIVE"));
    }

    @Test
    void accountServiceFailureIsPropagated() {
        when(accountClient.findAccount(ACCOUNT_ID))
                .thenThrow(new RemoteServiceException("account-service is unavailable"));

        assertThatThrownBy(() -> paymentOrderService.create(command("pay-0021", "Acme Utility", "120.00")))
                .isInstanceOf(RemoteServiceException.class);
    }

    @Test
    void invalidAmountsAreRejectedBeforeCallingDownstream() {
        for (String amount : List.of("0.00", "-5.00", "10.123")) {
            assertThatThrownBy(() -> paymentOrderService.create(command("pay-0022-" + amount, "Acme Utility", amount)))
                    .isInstanceOfSatisfying(BusinessRuleException.class,
                            ex -> assertThat(ex.getCode()).isEqualTo("INVALID_PAYMENT_AMOUNT"));
        }
        verify(accountClient, never()).findAccount(anyString());
    }

    @Test
    void invalidCurrencyIsRejected() {
        assertThatThrownBy(() -> paymentOrderService.create(new CreatePaymentOrderCommand(
                "pay-0023", ACCOUNT_ID, "Acme Utility", "1234567890123456", "BOFAUS3N",
                new BigDecimal("120.00"), "US", "ACH", "INV-2026-10", "Utility bill")))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("INVALID_CURRENCY"));
    }

    @Test
    void invalidPaymentTypeIsRejected() {
        assertThatThrownBy(() -> paymentOrderService.create(new CreatePaymentOrderCommand(
                "pay-0024", ACCOUNT_ID, "Acme Utility", "1234567890123456", "BOFAUS3N",
                new BigDecimal("120.00"), "USD", "CASH", "INV-2026-10", "Utility bill")))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("INVALID_PAYMENT_TYPE"));
    }

    @Test
    void missingIdempotencyKeyIsRejected() {
        assertThatThrownBy(() -> paymentOrderService.create(command("  ", "Acme Utility", "120.00")))
                .isInstanceOfSatisfying(InvalidRequestException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("IDEMPOTENCY_KEY_REQUIRED"));
    }

    @Test
    void listsOrdersFilteredByStatusAndAccount() {
        paymentOrderService.create(command("pay-0025", "Acme Utility", "120.00"));
        paymentOrderService.create(command("pay-0026", "Acme Fraud Ltd", "45.00"));

        PageResult<PaymentOrder> all = paymentOrderService.list(null, null, 0, 10);
        assertThat(all.total()).isEqualTo(2);
        assertThat(all.items()).hasSize(2);

        PageResult<PaymentOrder> screened = paymentOrderService.list("screened", null, 0, 10);
        assertThat(screened.total()).isEqualTo(1);
        assertThat(screened.items()).singleElement()
                .satisfies(order -> assertThat(order.getStatus()).isEqualTo(PaymentStatus.SCREENED));

        PageResult<PaymentOrder> rejected = paymentOrderService.list("REJECTED", ACCOUNT_ID, 0, 10);
        assertThat(rejected.total()).isEqualTo(1);
        assertThat(rejected.items()).singleElement()
                .satisfies(order -> assertThat(order.getStatus()).isEqualTo(PaymentStatus.REJECTED));

        PageResult<PaymentOrder> otherAccount = paymentOrderService.list(null, "00000000001", 0, 10);
        assertThat(otherAccount.total()).isZero();
    }

    @Test
    void invalidStatusFilterIsRejected() {
        assertThatThrownBy(() -> paymentOrderService.list("DONE", null, 0, 10))
                .isInstanceOfSatisfying(InvalidRequestException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("INVALID_PAYMENT_STATUS"));
    }

    @Test
    void unknownPaymentOrderIsNotFound() {
        assertThatThrownBy(() -> paymentOrderService.get("unknown-payment"))
                .isInstanceOfSatisfying(NotFoundException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("PAYMENT_NOT_FOUND"));
    }

    private PaymentOrder acceptedOrder(String idempotencyKey) {
        PaymentOrder order = paymentOrderService.create(command(idempotencyKey, "Acme Utility", "120.00"));
        return paymentOrderService.accept(order.getPaymentId());
    }

    private PaymentOrder settledOrder(String idempotencyKey) {
        PaymentOrder accepted = acceptedOrder(idempotencyKey);
        return paymentOrderService.settle(accepted.getPaymentId());
    }

    private CreatePaymentOrderCommand command(String idempotencyKey, String creditorName, String amount) {
        return new CreatePaymentOrderCommand(idempotencyKey, ACCOUNT_ID, creditorName, "1234567890123456",
                "BOFAUS3N", new BigDecimal(amount), "USD", "ACH", "INV-2026-10", "Utility bill");
    }

    private String storedStatus(String paymentId) {
        return jdbcTemplate.queryForObject("select status from payment_order where payment_id = ?",
                String.class, paymentId);
    }

    private String storedBatchId(String paymentId) {
        return jdbcTemplate.queryForObject("select transaction_batch_id from payment_order where payment_id = ?",
                String.class, paymentId);
    }
}
