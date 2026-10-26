package com.carddemo.search.interfaces.rest.dto;

import com.carddemo.search.domain.model.SearchItem;
import com.carddemo.search.domain.model.SearchResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.util.List;

/**
 * 统一搜索响应（docs/design/16-channel-and-quality.md §3.1）。
 *
 * @param total    命中总数（limit 截断前）
 * @param degraded 本次搜索降级的下游服务名
 */
public record SearchResponse(
        List<Item> items,
        long total,
        List<String> degraded) {

    public static SearchResponse from(SearchResult result) {
        return new SearchResponse(
                result.items().stream().map(Item::from).toList(),
                result.total(),
                result.degraded());
    }

    public record Item(
            @Schema(description = "结果类型", example = "CUSTOMER") String type,
            @Schema(description = "客户号或交易号", example = "000000001") String id,
            String title,
            String subtitle,
            BigDecimal amount,
            String reference) {

        static Item from(SearchItem item) {
            return new Item(item.type(), item.id(), item.title(), item.subtitle(),
                    item.amount(), item.reference());
        }
    }
}
