package com.carddemo.aml.interfaces.rest;

import com.carddemo.aml.application.AmlApplicationService;
import com.carddemo.aml.domain.model.AmlAlert;
import com.carddemo.aml.domain.repository.PageResult;
import com.carddemo.aml.interfaces.rest.dto.AlertListResponse;
import com.carddemo.aml.interfaces.rest.dto.AlertResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * AML 警报入站适配器。契约见 docs/design/14-risk-credit-collections.md §4.2。
 */
@Tag(name = "AML 警报", description = "警报查询与人工处置（排除/上报）")
@RestController
@RequestMapping("/api/v1/aml-alerts")
public class AmlAlertController {

    private final AmlApplicationService amlService;

    public AmlAlertController(AmlApplicationService amlService) {
        this.amlService = amlService;
    }

    @Operation(summary = "警报列表", description = "status 可选：OPEN/CLEARED/ESCALATED。")
    @GetMapping
    public AlertListResponse list(@RequestParam(required = false) String status,
                                  @RequestParam(defaultValue = "0") int page,
                                  @RequestParam(defaultValue = "10") int size) {
        PageResult<AmlAlert> result = amlService.listAlerts(status, page, size);
        return AlertListResponse.from(result, page, size);
    }

    @Operation(summary = "排除警报", description = "OPEN → CLEARED；终态再操作返回 409。")
    @PostMapping("/{alertId}/clear")
    public AlertResponse clear(@PathVariable String alertId) {
        return AlertResponse.from(amlService.clearAlert(alertId));
    }

    @Operation(summary = "上报警报", description = "OPEN → ESCALATED；终态再操作返回 409。")
    @PostMapping("/{alertId}/escalate")
    public AlertResponse escalate(@PathVariable String alertId) {
        return AlertResponse.from(amlService.escalateAlert(alertId));
    }
}
