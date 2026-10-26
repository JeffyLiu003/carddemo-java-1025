package com.carddemo.account.interfaces.rest;

import com.carddemo.account.application.AccountProductQueryService;
import com.carddemo.account.interfaces.rest.dto.AccountProductResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/account-products")
public class AccountProductController {

    private final AccountProductQueryService products;

    public AccountProductController(AccountProductQueryService products) {
        this.products = products;
    }

    @GetMapping
    public Map<String, List<AccountProductResponse>> list() {
        List<AccountProductResponse> items = products.findAll().stream()
                .map(AccountProductResponse::from)
                .toList();
        return Map.of("items", items);
    }

    @GetMapping("/{productCode}")
    public AccountProductResponse get(@PathVariable String productCode) {
        return AccountProductResponse.from(products.getByCode(productCode));
    }
}
