package com.carddemo.search.domain.model;

import java.util.List;

/**
 * 统一搜索结果。
 *
 * @param total    命中总数（limit 截断前）
 * @param degraded 本次搜索降级的下游服务名
 */
public record SearchResult(
        List<SearchItem> items,
        long total,
        List<String> degraded) {
}
