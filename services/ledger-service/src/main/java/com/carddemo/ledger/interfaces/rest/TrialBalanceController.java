package com.carddemo.ledger.interfaces.rest;

import com.carddemo.ledger.application.TrialBalanceService;
import com.carddemo.ledger.domain.model.AccountingPeriod;
import com.carddemo.ledger.interfaces.rest.dto.TrialBalanceResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "试算平衡", description = "按会计期间汇总每个科目的借贷发生额与净额")
@RestController
@RequestMapping("/api/v1/trial-balance")
public class TrialBalanceController {

    private final TrialBalanceService trialBalance;

    public TrialBalanceController(TrialBalanceService trialBalance) {
        this.trialBalance = trialBalance;
    }

    @Operation(summary = "试算平衡", description = "period 形如 2026-10；已冲正分录与冲正分录同时计入，净额自动归零")
    @GetMapping
    public TrialBalanceResponse trialBalance(@RequestParam String period) {
        return TrialBalanceResponse.from(trialBalance.trialBalance(AccountingPeriod.parsePeriod(period)));
    }
}
