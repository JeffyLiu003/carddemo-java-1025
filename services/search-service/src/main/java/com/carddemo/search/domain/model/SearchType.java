package com.carddemo.search.domain.model;

import com.carddemo.search.domain.exception.InvalidRequestException;

import java.util.Locale;

/**
 * 搜索类型。ALL 同时查客户与交易，CUSTOMER/TRANSACTION 只查对应下游。
 */
public enum SearchType {

    ALL,
    CUSTOMER,
    TRANSACTION;

    public static final String INVALID_CODE = "INVALID_SEARCH_TYPE";

    public static SearchType parse(String value) {
        if (value == null || value.isBlank()) {
            return ALL;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new InvalidRequestException(INVALID_CODE,
                    "Search type must be one of ALL, CUSTOMER, TRANSACTION");
        }
    }

    public boolean includesCustomers() {
        return this == ALL || this == CUSTOMER;
    }

    public boolean includesTransactions() {
        return this == ALL || this == TRANSACTION;
    }
}
