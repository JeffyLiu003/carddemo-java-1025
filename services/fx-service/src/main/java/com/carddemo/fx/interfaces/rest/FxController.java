package com.carddemo.fx.interfaces.rest;

import com.carddemo.fx.application.FxApplicationService;
import com.carddemo.fx.interfaces.rest.dto.FxConversionListResponse;
import com.carddemo.fx.interfaces.rest.dto.FxConversionResponse;
import com.carddemo.fx.interfaces.rest.dto.FxQuoteResponse;
import com.carddemo.fx.interfaces.rest.dto.FxRateListResponse;
import com.carddemo.fx.interfaces.rest.dto.FxRateResponse;
import com.carddemo.fx.interfaces.rest.dto.QuoteRequest;
import com.carddemo.fx.interfaces.rest.dto.UpsertRateRequest;
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

@Tag(name = "汇率与换汇", description = "汇率维护、5 分钟有效报价、单次消费的换汇执行与换汇记录")
@RestController
@RequestMapping("/api/v1/fx")
public class FxController {

    private final FxApplicationService fx;

    public FxController(FxApplicationService fx) {
        this.fx = fx;
    }

    @Operation(summary = "汇率列表", description = "按 base/quote 升序，可选分页（默认 0/20）")
    @GetMapping("/rates")
    public FxRateListResponse listRates(@RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "20") int size) {
        return FxRateListResponse.from(fx.listRates(page, size));
    }

    @Operation(summary = "维护汇率",
            description = "按 (baseCurrency, quoteCurrency) upsert：币种 3 位大写、汇率大于零且保留 6 位小数。")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(examples = @ExampleObject(
            name = "维护 USD/CNY",
            value = """
                    {"baseCurrency":"USD","quoteCurrency":"CNY","rate":7.100000,
                     "effectiveAt":"2026-10-26T00:00:00"}
                    """)))
    @PostMapping("/rates")
    public FxRateResponse upsertRate(@Valid @RequestBody UpsertRateRequest request) {
        return FxRateResponse.from(fx.upsertRate(request.toCommand()));
    }

    @Operation(summary = "报价",
            description = "金额大于零、币种不同、直接货币对汇率存在；convertedAmount = amount × rate（HALF_UP 2 位），"
                    + "expiresAt = createdAt + 5 分钟。")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(examples = @ExampleObject(
            name = "USD 换 CNY",
            value = """
                    {"fromCurrency":"USD","toCurrency":"CNY","amount":100.00}
                    """)))
    @PostMapping("/quotes")
    public ResponseEntity<FxQuoteResponse> quote(@Valid @RequestBody QuoteRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(FxQuoteResponse.from(fx.quote(request.toCommand())));
    }

    @Operation(summary = "执行换汇",
            description = "报价未过期且未消费；重复执行 409 QUOTE_ALREADY_CONSUMED，过期 409 QUOTE_EXPIRED。")
    @PostMapping("/quotes/{quoteId}/execute")
    public ResponseEntity<FxConversionResponse> execute(@PathVariable String quoteId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(FxConversionResponse.from(fx.execute(quoteId)));
    }

    @Operation(summary = "换汇记录", description = "按创建时间倒序，可选分页（默认 0/20）")
    @GetMapping("/conversions")
    public FxConversionListResponse listConversions(@RequestParam(defaultValue = "0") int page,
                                                    @RequestParam(defaultValue = "20") int size) {
        return FxConversionListResponse.from(fx.listConversions(page, size));
    }
}
