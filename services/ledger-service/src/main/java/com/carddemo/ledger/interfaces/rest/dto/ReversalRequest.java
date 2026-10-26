package com.carddemo.ledger.interfaces.rest.dto;

import java.time.LocalDateTime;

/**
 * 冲正请求体可省略；仅当需要指定冲正记账时间（切换到其它 OPEN 期间）时传 postedAt。
 */
public record ReversalRequest(LocalDateTime postedAt) {
}
