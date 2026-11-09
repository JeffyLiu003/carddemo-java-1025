package com.carddemo.marketing.application;

import java.util.List;

/**
 * 退款与冲正受理结果：一次请求可能命中同一交易上的多条奖励（多活动并存）。
 */
public record RewardReversalListView(List<RewardReversalView> reversals) {
}
