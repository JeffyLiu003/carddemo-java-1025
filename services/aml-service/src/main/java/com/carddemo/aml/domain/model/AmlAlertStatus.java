package com.carddemo.aml.domain.model;

/**
 * 警报状态。OPEN 为唯一可处置状态，清除/上报后不可再变更。
 */
public enum AmlAlertStatus {

    OPEN,
    CLEARED,
    ESCALATED
}
