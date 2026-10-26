package com.carddemo.transaction.interfaces.rest;

import com.carddemo.transaction.application.PostingTypeService;
import com.carddemo.transaction.interfaces.rest.dto.PostingTypeDetailResponse;
import com.carddemo.transaction.interfaces.rest.dto.PostingTypeResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Tag(name = "过账类型", description = "过账类型与分录规则（A1.2）")
@RestController
@RequestMapping("/api/v1/posting-types")
public class PostingTypeController {

    private final PostingTypeService postingTypeService;

    public PostingTypeController(PostingTypeService postingTypeService) {
        this.postingTypeService = postingTypeService;
    }

    @Operation(summary = "过账类型列表", description = "不含规则；含停用标记。")
    @GetMapping
    public Map<String, List<PostingTypeResponse>> list() {
        List<PostingTypeResponse> items = postingTypeService.list().stream()
                .map(PostingTypeResponse::from)
                .toList();
        return Map.of("items", items);
    }

    @Operation(summary = "过账类型详情", description = "类型 + 按 legNo 升序的分录规则。")
    @GetMapping("/{typeCode}")
    public PostingTypeDetailResponse get(@PathVariable String typeCode) {
        return PostingTypeDetailResponse.from(postingTypeService.get(typeCode));
    }
}
