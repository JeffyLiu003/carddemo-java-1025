package com.carddemo.fraud.interfaces.rest;

import com.carddemo.fraud.application.EvaluationOutcome;
import com.carddemo.fraud.application.FraudApplicationService;
import com.carddemo.fraud.interfaces.rest.dto.CreateEvaluationRequest;
import com.carddemo.fraud.interfaces.rest.dto.EvaluationResponse;
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
 * 欺诈评分入站适配器。契约见 docs/design/14-risk-credit-collections.md §3.2。
 */
@Tag(name = "欺诈评分", description = "交易评分、规则命中与评分详情")
@RestController
@RequestMapping("/api/v1/fraud-evaluations")
public class FraudEvaluationController {

    private final FraudApplicationService fraudService;

    public FraudEvaluationController(FraudApplicationService fraudService) {
        this.fraudService = fraudService;
    }

    @Operation(summary = "交易欺诈评分",
            description = "命中规则分值求和：≥80 REJECT、≥50 REVIEW、其余 APPROVE；REVIEW/REJECT 生成 OPEN 警报。")
    @PostMapping
    public ResponseEntity<EvaluationResponse> evaluate(
            @Valid @RequestBody CreateEvaluationRequest request) {
        EvaluationOutcome outcome = fraudService.evaluate(request.toCommand());
        return ResponseEntity.status(HttpStatus.CREATED).body(EvaluationResponse.from(outcome));
    }

    @GetMapping("/{evaluationId}")
    public EvaluationResponse get(@PathVariable String evaluationId) {
        return EvaluationResponse.from(fraudService.getEvaluationDetail(evaluationId));
    }
}
