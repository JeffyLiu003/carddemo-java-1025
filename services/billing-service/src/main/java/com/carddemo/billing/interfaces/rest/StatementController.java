package com.carddemo.billing.interfaces.rest;

import com.carddemo.billing.application.StatementService;
import com.carddemo.billing.interfaces.rest.dto.GenerateStatementRequest;
import com.carddemo.billing.interfaces.rest.dto.StatementListResponse;
import com.carddemo.billing.interfaces.rest.dto.StatementResponse;
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

@Tag(name = "账单", description = "账单生成、最低还款与利息试算、到期日、全额账单支付")
@RestController
@RequestMapping("/api/v1/statements")
public class StatementController {

    private final StatementService statements;

    public StatementController(StatementService statements) {
        this.statements = statements;
    }

    @GetMapping
    public StatementListResponse list(@RequestParam(required = false) String accountId,
                                      @RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "20") int size) {
        int safePage = Math.max(page, 0);
        int safeSize = size <= 0 ? 20 : size;
        return StatementListResponse.from(statements.list(accountId, safePage, safeSize), safePage, safeSize);
    }

    @GetMapping("/{statementId}")
    public StatementResponse get(@PathVariable String statementId) {
        return StatementResponse.from(statements.get(statementId));
    }

    @Operation(summary = "生成账单",
            description = "读取 account-service 的账户额度与产品 APR 计算账单；同账户+期间只生成一张，重复调用返回已有账单。")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(examples = @ExampleObject(
            name = "生成 2026-10 账单",
            value = """
                    {"accountId":"00000000001","period":"2026-10"}
                    """)))
    @PostMapping("/generate")
    public ResponseEntity<StatementResponse> generate(@Valid @RequestBody GenerateStatementRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(StatementResponse.from(statements.generate(request.toCommand())));
    }

    @Operation(summary = "全额支付账单",
            description = "closing 为 0 直接置 PAID；否则调用 account-service /payments 支付 closing，成功后置 PAID。重复调用不重复支付。")
    @PostMapping("/{statementId}/pay")
    public ResponseEntity<StatementResponse> pay(@PathVariable String statementId) {
        return ResponseEntity.ok(StatementResponse.from(statements.pay(statementId)));
    }
}
