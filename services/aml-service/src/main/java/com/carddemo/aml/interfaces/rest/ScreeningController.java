package com.carddemo.aml.interfaces.rest;

import com.carddemo.aml.application.AmlApplicationService;
import com.carddemo.aml.application.ScreeningOutcome;
import com.carddemo.aml.interfaces.rest.dto.CreateScreeningRequest;
import com.carddemo.aml.interfaces.rest.dto.ScreeningResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 名单筛查入站适配器。契约见 docs/design/14-risk-credit-collections.md §4.2。
 */
@Tag(name = "名单筛查", description = "制裁/名单筛查与筛查详情")
@RestController
@RequestMapping("/api/v1/screenings")
public class ScreeningController {

    private final AmlApplicationService amlService;

    public ScreeningController(AmlApplicationService amlService) {
        this.amlService = amlService;
    }

    @Operation(summary = "名单筛查",
            description = "姓名归一化后比对：完全匹配 BLOCKED、包含匹配 POTENTIAL_MATCH、否则 CLEAR；"
                    + "命中生成 OPEN 警报。")
    @PostMapping
    public ResponseEntity<ScreeningResponse> screen(
            @Valid @RequestBody CreateScreeningRequest request) {
        ScreeningOutcome outcome = amlService.screen(request.toCommand());
        return ResponseEntity.status(HttpStatus.CREATED).body(ScreeningResponse.from(outcome));
    }

    @GetMapping("/{screeningId}")
    public ScreeningResponse get(@PathVariable String screeningId) {
        return ScreeningResponse.from(amlService.getScreeningDetail(screeningId));
    }
}
