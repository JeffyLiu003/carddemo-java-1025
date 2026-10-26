package com.carddemo.ledger.interfaces.rest;

import com.carddemo.ledger.application.LedgerAccountQueryService;
import com.carddemo.ledger.interfaces.rest.dto.LedgerAccountResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Tag(name = "科目表", description = "会计科目（COA）：资产/负债/权益/收入/费用")
@RestController
@RequestMapping("/api/v1/ledger-accounts")
public class LedgerAccountController {

    private final LedgerAccountQueryService accounts;

    public LedgerAccountController(LedgerAccountQueryService accounts) {
        this.accounts = accounts;
    }

    @Operation(summary = "科目表", description = "按科目号升序返回全部科目，含停用科目")
    @GetMapping
    public Map<String, List<LedgerAccountResponse>> list() {
        List<LedgerAccountResponse> items = accounts.findAll().stream()
                .map(LedgerAccountResponse::from)
                .toList();
        return Map.of("items", items);
    }

    @GetMapping("/{code}")
    public LedgerAccountResponse get(@PathVariable String code) {
        return LedgerAccountResponse.from(accounts.getByCode(code));
    }
}
