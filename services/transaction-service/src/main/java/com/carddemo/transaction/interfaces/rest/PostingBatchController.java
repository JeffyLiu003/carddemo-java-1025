package com.carddemo.transaction.interfaces.rest;

import com.carddemo.transaction.application.PostingBatchService;
import com.carddemo.transaction.application.PostingBatchView;
import com.carddemo.transaction.domain.repository.PageResult;
import com.carddemo.transaction.interfaces.rest.dto.CreatePostingBatchRequest;
import com.carddemo.transaction.interfaces.rest.dto.PostingBatchListResponse;
import com.carddemo.transaction.interfaces.rest.dto.PostingBatchResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "过账批次", description = "过账引擎：借贷分录、账户记账、总账同步与冲正（A1.2）")
@RestController
@RequestMapping("/api/v1/posting-batches")
public class PostingBatchController {

    private final PostingBatchService postingBatchService;

    public PostingBatchController(PostingBatchService postingBatchService) {
        this.postingBatchService = postingBatchService;
    }

    @Operation(summary = "创建过账批次",
            description = "同一幂等键且类型/账户/金额一致返回原批次；账户记账成功后落库；总账同步失败不影响过账。")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(examples = @ExampleObject(
            name = "信用卡消费 25.00（DR 1100 / CR 2100）",
            value = """
                    {"idempotencyKey":"post-20261026-0001","typeCode":"CARD_PURCHASE",
                    "accountId":"00000000006","amount":25.00,"currency":"USD",
                    "description":"Purchase at Corner Grocery"}
                    """)))
    @PostMapping
    public ResponseEntity<PostingBatchResponse> create(@Valid @RequestBody CreatePostingBatchRequest request) {
        PostingBatchView view = postingBatchService.create(request.toCommand());
        return ResponseEntity.status(HttpStatus.CREATED).body(PostingBatchResponse.from(view));
    }

    @GetMapping("/{batchId}")
    public PostingBatchResponse get(@PathVariable String batchId) {
        return PostingBatchResponse.from(postingBatchService.get(batchId));
    }

    @Operation(summary = "过账批次分页查询", description = "可按 accountId 与 idempotencyKey 过滤，按创建时间倒序。")
    @GetMapping
    public PostingBatchListResponse list(@RequestParam(required = false) String accountId,
                                         @RequestParam(required = false) String idempotencyKey,
                                         @RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "10") int size) {
        int safePage = Math.max(page, 0);
        int safeSize = size <= 0 ? 10 : size;
        PageResult<PostingBatchView> result = postingBatchService.list(accountId, idempotencyKey,
                safePage, safeSize);
        return PostingBatchListResponse.from(result, safePage, safeSize);
    }

    @Operation(summary = "冲正过账批次",
            description = "生成方向互换的冲正批次并反向调整账户；重复冲正返回已有冲正批次；冲正批次不可再冲正。")
    @PostMapping("/{batchId}/reversal")
    public PostingBatchResponse reverse(@PathVariable String batchId) {
        return PostingBatchResponse.from(postingBatchService.reverse(batchId));
    }
}
