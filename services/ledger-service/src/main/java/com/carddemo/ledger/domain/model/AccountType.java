package com.carddemo.ledger.domain.model;

/**
 * 会计科目类型。试算平衡按类型可判断正常余额方向（资产/费用借方，负债/权益/收入贷方）。
 */
public enum AccountType {
    ASSET,
    LIABILITY,
    EQUITY,
    REVENUE,
    EXPENSE
}
