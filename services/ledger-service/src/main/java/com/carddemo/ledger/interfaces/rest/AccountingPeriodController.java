package com.carddemo.ledger.interfaces.rest;

import com.carddemo.ledger.application.AccountingPeriodService;
import com.carddemo.ledger.interfaces.rest.dto.AccountingPeriodResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Tag(name = "会计期间", description = "期间列表与关账")
@RestController
@RequestMapping("/api/v1/accounting-periods")
public class AccountingPeriodController {

    private final AccountingPeriodService periods;

    public AccountingPeriodController(AccountingPeriodService periods) {
        this.periods = periods;
    }

    @GetMapping
    public Map<String, List<AccountingPeriodResponse>> list() {
        List<AccountingPeriodResponse> items = periods.findAll().stream()
                .map(AccountingPeriodResponse::from)
                .toList();
        return Map.of("items", items);
    }

    @Operation(summary = "关账", description = "仅 OPEN 可关；期间不存在 404 PERIOD_NOT_FOUND，已关账 409 PERIOD_ALREADY_CLOSED")
    @PostMapping("/{period}/close")
    public AccountingPeriodResponse close(@PathVariable String period) {
        return AccountingPeriodResponse.from(periods.close(period));
    }
}
