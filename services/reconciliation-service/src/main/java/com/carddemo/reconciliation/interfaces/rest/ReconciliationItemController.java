package com.carddemo.reconciliation.interfaces.rest;

import com.carddemo.reconciliation.application.ReconciliationService;
import com.carddemo.reconciliation.interfaces.rest.dto.ReconciliationItemResponse;
import com.carddemo.reconciliation.interfaces.rest.dto.ResolveItemRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "对账差异处理", description = "登记差异处理说明；只更新对账记录，不修改账务数据")
@RestController
@RequestMapping("/api/v1/reconciliation-items")
public class ReconciliationItemController {

    private final ReconciliationService reconciliations;

    public ReconciliationItemController(ReconciliationService reconciliations) {
        this.reconciliations = reconciliations;
    }

    @Operation(summary = "登记差异处理", description = "置 resolved=true 并保存说明；明细不存在返回 404 RECONCILIATION_ITEM_NOT_FOUND")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(examples = @ExampleObject(
            name = "登记处理说明",
            value = """
                    {"note":"已确认总账同步延迟，手工补录"}
                    """)))
    @PostMapping("/{itemId}/resolve")
    public ReconciliationItemResponse resolve(@PathVariable String itemId,
                                              @Valid @RequestBody ResolveItemRequest request) {
        return ReconciliationItemResponse.from(reconciliations.resolve(itemId, request.note()));
    }
}
