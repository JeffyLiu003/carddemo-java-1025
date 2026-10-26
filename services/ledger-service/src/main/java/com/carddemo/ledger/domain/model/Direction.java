package com.carddemo.ledger.domain.model;

/**
 * 记账方向：DR 借方、CR 贷方。
 */
public enum Direction {
    DR,
    CR;

    public Direction opposite() {
        return this == DR ? CR : DR;
    }
}
