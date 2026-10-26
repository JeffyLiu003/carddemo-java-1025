package com.carddemo.payment.domain.model;

import com.carddemo.payment.domain.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 支付指令聚合：受理校验与状态机（设计 §2.1–2.2）。
 */
class PaymentOrderTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 26, 9, 0);

    @Test
    void receiveValidatesAmountCurrencyAndNormalizes() {
        PaymentOrder order = order("Acme Utility", new BigDecimal("120.5"), "usd");

        assertThat(order.getStatus()).isEqualTo(PaymentStatus.RECEIVED);
        assertThat(order.getAmount()).isEqualByComparingTo("120.50");
        assertThat(order.getCurrency()).isEqualTo("USD");
        assertThat(order.getPaymentType()).isEqualTo(PaymentType.ACH);
        assertThat(order.getCreatedAt()).isEqualTo(NOW);
        assertThat(order.getUpdatedAt()).isEqualTo(NOW);
    }

    @Test
    void receiveRejectsInvalidAmounts() {
        for (String amount : List.of("0.00", "-5.00", "10.123")) {
            assertThatThrownBy(() -> order("Acme Utility", new BigDecimal(amount), "USD"))
                    .isInstanceOfSatisfying(BusinessRuleException.class,
                            ex -> assertThat(ex.getCode()).isEqualTo("INVALID_PAYMENT_AMOUNT"));
        }
        assertThatThrownBy(() -> order("Acme Utility", null, "USD"))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("INVALID_PAYMENT_AMOUNT"));
    }

    @Test
    void receiveRejectsInvalidCurrency() {
        for (String currency : List.of("US", "USDD", "12A")) {
            assertThatThrownBy(() -> order("Acme Utility", new BigDecimal("10.00"), currency))
                    .isInstanceOfSatisfying(BusinessRuleException.class,
                            ex -> assertThat(ex.getCode()).isEqualTo("INVALID_CURRENCY"));
        }
    }

    @Test
    void screenAndRejectMoveReceivedOrder() {
        PaymentOrder screened = order("Acme Utility", new BigDecimal("10.00"), "USD");
        screened.screen(NOW.plusMinutes(1));
        assertThat(screened.getStatus()).isEqualTo(PaymentStatus.SCREENED);
        assertThat(screened.getUpdatedAt()).isEqualTo(NOW.plusMinutes(1));

        PaymentOrder rejected = order("ACME FRAUD LTD", new BigDecimal("10.00"), "USD");
        rejected.reject("CREDITOR_BLOCKED", NOW.plusMinutes(2));
        assertThat(rejected.getStatus()).isEqualTo(PaymentStatus.REJECTED);
        assertThat(rejected.getRejectReason()).isEqualTo("CREDITOR_BLOCKED");
        assertThat(rejected.getStatus().isTerminal()).isTrue();
    }

    @Test
    void screenCannotBeRepeated() {
        PaymentOrder order = order("Acme Utility", new BigDecimal("10.00"), "USD");
        order.screen(NOW);

        assertThatThrownBy(() -> order.screen(NOW))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("INVALID_PAYMENT_STATUS"));
        assertThatThrownBy(() -> order.reject("CREDITOR_BLOCKED", NOW))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("INVALID_PAYMENT_STATUS"));
    }

    @Test
    void acceptRequiresScreenedStatus() {
        PaymentOrder order = order("Acme Utility", new BigDecimal("10.00"), "USD");

        assertThatThrownBy(() -> order.accept(NOW))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("PAYMENT_NOT_SCREENED"));

        order.screen(NOW);
        order.accept(NOW.plusMinutes(1));
        assertThat(order.getStatus()).isEqualTo(PaymentStatus.ACCEPTED);
    }

    @Test
    void settleRequiresAcceptedStatusAndRecordsBatch() {
        PaymentOrder order = order("Acme Utility", new BigDecimal("10.00"), "USD");
        order.screen(NOW);

        assertThatThrownBy(() -> order.settle("BATCH-1", NOW))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("PAYMENT_NOT_ACCEPTED"));

        order.accept(NOW.plusMinutes(1));
        order.settle("BATCH-1", NOW.plusMinutes(2));
        assertThat(order.getStatus()).isEqualTo(PaymentStatus.SETTLED);
        assertThat(order.getTransactionBatchId()).isEqualTo("BATCH-1");
        assertThat(order.getSettledAt()).isEqualTo(NOW.plusMinutes(2));
    }

    @Test
    void returnRequiresAcceptedOrSettledStatus() {
        PaymentOrder order = order("Acme Utility", new BigDecimal("10.00"), "USD");
        order.screen(NOW);

        assertThatThrownBy(() -> order.returnPayment(NOW))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("PAYMENT_NOT_RETURNABLE"));

        order.accept(NOW.plusMinutes(1));
        order.returnPayment(NOW.plusMinutes(2));
        assertThat(order.getStatus()).isEqualTo(PaymentStatus.RETURNED);
        assertThat(order.getReturnedAt()).isEqualTo(NOW.plusMinutes(2));
        assertThat(order.getStatus().isTerminal()).isTrue();
    }

    @Test
    void paymentTypeRequiresKnownCode() {
        assertThat(PaymentType.require("instant")).isEqualTo(PaymentType.INSTANT);
        assertThatThrownBy(() -> PaymentType.require("CASH"))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("INVALID_PAYMENT_TYPE"));
    }

    private static PaymentOrder order(String creditorName, BigDecimal amount, String currency) {
        return PaymentOrder.receive("pay-1", "pay-1-key", "00000000006", creditorName,
                "1234567890123456", "BOFAUS3N", amount, currency, PaymentType.ACH, "INV-2026-10",
                "Utility bill", NOW);
    }
}
