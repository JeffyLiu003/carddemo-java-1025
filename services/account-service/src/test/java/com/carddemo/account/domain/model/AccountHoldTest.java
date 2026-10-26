package com.carddemo.account.domain.model;

import com.carddemo.account.domain.exception.BusinessRuleException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountHoldTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 1, 1, 10, 0);

    private AccountHold hold() {
        return AccountHold.create("h-1", "00000000006", new BigDecimal("100.00"),
                "MANUAL", "ref-1", NOW, NOW.plusHours(2));
    }

    @Test
    void createsActiveHold() {
        AccountHold hold = hold();

        assertThat(hold.getStatus()).isEqualTo(HoldStatus.ACTIVE);
        assertThat(hold.getAmount()).isEqualByComparingTo("100.00");
        assertThat(hold.getReleasedAt()).isNull();
    }

    @Test
    void rejectsNonPositiveAmount() {
        assertThatThrownBy(() -> AccountHold.create("h-1", "00000000006", BigDecimal.ZERO,
                "MANUAL", "ref-1", NOW, null))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code").isEqualTo("INVALID_HOLD_AMOUNT");
    }

    @Test
    void rejectsExpiryNotAfterCreation() {
        assertThatThrownBy(() -> AccountHold.create("h-1", "00000000006", new BigDecimal("1.00"),
                "MANUAL", "ref-1", NOW, NOW))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code").isEqualTo("INVALID_HOLD_EXPIRY");
    }

    @Test
    void releaseMarksHoldReleased() {
        AccountHold hold = hold();

        hold.release(NOW.plusMinutes(5));

        assertThat(hold.getStatus()).isEqualTo(HoldStatus.RELEASED);
        assertThat(hold.getReleasedAt()).isEqualTo(NOW.plusMinutes(5));
    }

    @Test
    void releasedHoldCannotBeReleasedAgain() {
        AccountHold hold = hold();
        hold.release(NOW.plusMinutes(5));

        assertThatThrownBy(() -> hold.release(NOW.plusMinutes(6)))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code").isEqualTo("HOLD_NOT_RELEASABLE");
    }

    @Test
    void expiredHoldIsDetectedByTime() {
        AccountHold hold = hold();

        assertThat(hold.isExpired(NOW.plusHours(3))).isTrue();
        assertThat(hold.isExpired(NOW.plusHours(1))).isFalse();
    }
}
