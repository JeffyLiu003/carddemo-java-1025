package com.carddemo.account.interfaces.rest;

import com.carddemo.account.application.HoldApplicationService;
import com.carddemo.account.domain.model.AccountHold;
import com.carddemo.account.domain.model.HoldStatus;
import com.carddemo.account.interfaces.rest.dto.CreditPositionResponse;
import com.carddemo.account.interfaces.rest.dto.HoldRequest;
import com.carddemo.account.interfaces.rest.dto.HoldResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/accounts/{accountId}")
public class AccountHoldController {

    private final HoldApplicationService holds;

    public AccountHoldController(HoldApplicationService holds) {
        this.holds = holds;
    }

    @GetMapping("/credit-position")
    public CreditPositionResponse creditPosition(@PathVariable String accountId) {
        return CreditPositionResponse.from(holds.creditPosition(accountId));
    }

    @GetMapping("/holds")
    public Map<String, List<HoldResponse>> list(@PathVariable String accountId,
                                                @RequestParam(required = false) HoldStatus status) {
        List<HoldResponse> items = holds.list(accountId, status).stream()
                .map(HoldResponse::from)
                .toList();
        return Map.of("items", items);
    }

    @PostMapping("/holds")
    public HoldResponse create(@PathVariable String accountId,
                               @Valid @RequestBody HoldRequest request) {
        AccountHold hold = holds.create(accountId, request.amount(), request.referenceType(),
                request.referenceId(), request.expiresAt());
        return HoldResponse.from(hold);
    }

    @DeleteMapping("/holds/{holdId}")
    public HoldResponse release(@PathVariable String accountId, @PathVariable String holdId) {
        return HoldResponse.from(holds.release(accountId, holdId));
    }
}
