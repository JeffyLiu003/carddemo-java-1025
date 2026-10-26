package com.carddemo.fraud.interfaces.rest;

import com.carddemo.fraud.application.FraudApplicationService;
import com.carddemo.fraud.domain.model.FraudAlert;
import com.carddemo.fraud.domain.repository.PageResult;
import com.carddemo.fraud.interfaces.rest.dto.AlertListResponse;
import com.carddemo.fraud.interfaces.rest.dto.AlertResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 欺诈警报入站适配器。契约见 docs/design/14-risk-credit-collections.md §3.2。
 */
@Tag(name = "欺诈警报", description = "警报查询与人工处置（确认欺诈/排除）")
@RestController
@RequestMapping("/api/v1/fraud-alerts")
public class FraudAlertController {

    private final FraudApplicationService fraudService;

    public FraudAlertController(FraudApplicationService fraudService) {
        this.fraudService = fraudService;
    }

    @Operation(summary = "警报列表", description = "status 可选：OPEN/CONFIRMED/DISMISSED；accountId 可选。")
    @GetMapping
    public AlertListResponse list(@RequestParam(required = false) String status,
                                  @RequestParam(required = false) String accountId,
                                  @RequestParam(defaultValue = "0") int page,
                                  @RequestParam(defaultValue = "10") int size) {
        PageResult<FraudAlert> result = fraudService.listAlerts(status, accountId, page, size);
        return AlertListResponse.from(result, page, size);
    }

    @GetMapping("/{alertId}")
    public AlertResponse get(@PathVariable String alertId) {
        return AlertResponse.from(fraudService.getAlert(alertId));
    }

    @Operation(summary = "确认欺诈", description = "OPEN → CONFIRMED；终态再操作返回 409。")
    @PostMapping("/{alertId}/confirm")
    public AlertResponse confirm(@PathVariable String alertId) {
        return AlertResponse.from(fraudService.confirmAlert(alertId));
    }

    @Operation(summary = "排除警报", description = "OPEN → DISMISSED；终态再操作返回 409。")
    @PostMapping("/{alertId}/dismiss")
    public AlertResponse dismiss(@PathVariable String alertId) {
        return AlertResponse.from(fraudService.dismissAlert(alertId));
    }
}
