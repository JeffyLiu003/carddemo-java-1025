package com.carddemo.transaction.domain.model;

/**
 * 复式分录方向：DR 借方，CR 贷方。
 */
public enum Direction {

    DR,
    CR;

    public Direction reverse() {
        return this == DR ? CR : DR;
    }
}
