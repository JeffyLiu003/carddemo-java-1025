package com.carddemo.payment.infrastructure.client;

import com.carddemo.payment.domain.model.AccountSnapshot;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * account-service 的 AccountResponse 投影（支付受理只关心账户号与有效标志）。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
record AccountBody(String accountId, String activeStatus) {

    AccountSnapshot toSnapshot() {
        return new AccountSnapshot(accountId, activeStatus);
    }
}
