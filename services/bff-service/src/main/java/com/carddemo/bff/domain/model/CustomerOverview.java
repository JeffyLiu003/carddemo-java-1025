package com.carddemo.bff.domain.model;

import java.util.List;

/**
 * 客户 360 聚合结果。position 为空表示账户不存在或不可用（此时不会构造本对象），
 * customer/offers 可能因无卡或下游降级而为空。
 *
 * @param degraded 聚合期间降级的下游服务名，按卡片、客户、交易、权益顺序去重记录
 */
public record CustomerOverview(
        String accountId,
        AccountPosition position,
        List<CardSummary> cards,
        CustomerProfile customer,
        List<RecentTransaction> recentTransactions,
        List<OfferSummary> offers,
        List<String> degraded) {
}
