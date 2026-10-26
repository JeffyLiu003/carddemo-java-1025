package com.carddemo.search.domain.model;

import java.math.BigDecimal;

/**
 * 统一搜索结果条目（docs/design/16-channel-and-quality.md §3.1）。
 *
 * <p>客户：id/客户号、title 为姓名、subtitle 为客户号、reference 为掩码 SSN。
 * 交易：id/交易号、title 为摘要、subtitle 为账户号、amount 为金额、reference 为卡号。
 */
public record SearchItem(
        String type,
        String id,
        String title,
        String subtitle,
        BigDecimal amount,
        String reference) {
}
