package com.carddemo.fraud.domain.model;

/**
 * 警报状态。OPEN 为唯一可处置状态，确认/排除后不可再变更。
 */
public enum FraudAlertStatus {

    OPEN,
    CONFIRMED,
    DISMISSED
}
