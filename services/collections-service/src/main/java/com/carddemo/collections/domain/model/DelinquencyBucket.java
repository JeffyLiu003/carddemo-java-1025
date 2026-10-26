package com.carddemo.collections.domain.model;

/**
 * 逾期分桶：1-30 B1、31-60 B2、61-90 B3、90 天以上 B4（设计 §6.2）。
 */
public enum DelinquencyBucket {
    B1,
    B2,
    B3,
    B4;

    public static DelinquencyBucket of(int daysPastDue) {
        if (daysPastDue <= 30) {
            return B1;
        }
        if (daysPastDue <= 60) {
            return B2;
        }
        if (daysPastDue <= 90) {
            return B3;
        }
        return B4;
    }
}
