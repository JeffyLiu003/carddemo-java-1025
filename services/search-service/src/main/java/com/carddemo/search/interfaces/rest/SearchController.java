package com.carddemo.search.interfaces.rest;

import com.carddemo.search.application.SearchService;
import com.carddemo.search.interfaces.rest.dto.SearchResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "统一搜索", description = "客户与交易统一搜索（跨服务实时过滤，无本地索引）")
@RestController
@RequestMapping("/api/v1/search")
@Validated
public class SearchController {

    private final SearchService search;

    public SearchController(SearchService search) {
        this.search = search;
    }

    @Operation(summary = "统一搜索",
            description = "type ∈ ALL/CUSTOMER/TRANSACTION（非法返回 400 INVALID_SEARCH_TYPE）。"
                    + "11 位纯数字按账户号查交易，16 位纯数字按卡号查交易，其他查询串不查交易。"
                    + "下游失败时对应类型为空并记录 degraded；查询类型明确且该下游失败返回 503。")
    @GetMapping
    public SearchResponse search(
            @RequestParam
            @NotBlank(message = "Search query must be supplied.")
            String q,
            @RequestParam(defaultValue = "ALL") String type,
            @RequestParam(defaultValue = "10")
            @Min(value = 1, message = "Limit must be between 1 and 50.")
            @Max(value = 50, message = "Limit must be between 1 and 50.")
            int limit) {
        return SearchResponse.from(search.search(q, type, limit));
    }
}
