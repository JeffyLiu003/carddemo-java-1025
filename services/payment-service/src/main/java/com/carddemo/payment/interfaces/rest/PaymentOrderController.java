package com.carddemo.payment.interfaces.rest;

import com.carddemo.payment.application.PaymentOrderService;
import com.carddemo.payment.domain.model.PaymentOrder;
import com.carddemo.payment.domain.repository.PageResult;
import com.carddemo.payment.interfaces.rest.dto.CreatePaymentOrderRequest;
import com.carddemo.payment.interfaces.rest.dto.PaymentOrderListResponse;
import com.carddemo.payment.interfaces.rest.dto.PaymentOrderResponse;
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

@Tag(name = "支付指令", description = "支付受理、自动筛查、接受、清算与退汇（设计 13 §2.2）")
@RestController
@RequestMapping("/api/v1/payment-orders")
public class PaymentOrderController {

    private final PaymentOrderService paymentOrderService;

    public PaymentOrderController(PaymentOrderService paymentOrderService) {
        this.paymentOrderService = paymentOrderService;
    }

    @Operation(summary = "受理支付指令",
            description = "同一 idempotencyKey 且请求一致返回原单，不一致 409 IDEMPOTENCY_CONFLICT；"
                    + "创建后自动筛查：creditorName 非空且不在拒绝名单 → SCREENED，否则 REJECTED。")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(examples = @ExampleObject(
            name = "ACH 缴费 120.00",
            value = """
                    {"idempotencyKey":"pay-20261026-0001","debtorAccountId":"00000000006",
                     "creditorName":"Acme Utility","creditorAccountNumber":"1234567890123456",
                     "creditorBankCode":"BOFAUS3N","amount":120.00,"currency":"USD",
                     "paymentType":"ACH","reference":"INV-2026-10","description":"Utility bill"}
                    """)))
    @PostMapping
    public ResponseEntity<PaymentOrderResponse> create(@Valid @RequestBody CreatePaymentOrderRequest request) {
        PaymentOrder order = paymentOrderService.create(request.toCommand());
        return ResponseEntity.status(HttpStatus.CREATED).body(PaymentOrderResponse.from(order));
    }

    @GetMapping("/{paymentId}")
    public PaymentOrderResponse get(@PathVariable String paymentId) {
        return PaymentOrderResponse.from(paymentOrderService.get(paymentId));
    }

    @Operation(summary = "支付指令分页查询", description = "可按 status 与 accountId 过滤，按创建时间倒序。")
    @GetMapping
    public PaymentOrderListResponse list(@RequestParam(required = false) String status,
                                         @RequestParam(required = false) String accountId,
                                         @RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "10") int size) {
        int safePage = Math.max(page, 0);
        int safeSize = size <= 0 ? 10 : size;
        PageResult<PaymentOrder> result = paymentOrderService.list(status, accountId, safePage, safeSize);
        return PaymentOrderListResponse.from(result, safePage, safeSize);
    }

    @Operation(summary = "接受支付指令", description = "仅允许 SCREENED；重复调用幂等返回当前单据。")
    @PostMapping("/{paymentId}/accept")
    public PaymentOrderResponse accept(@PathVariable String paymentId) {
        return PaymentOrderResponse.from(paymentOrderService.accept(paymentId));
    }

    @Operation(summary = "清算支付指令",
            description = "仅允许 ACCEPTED；以 payment:<paymentId> 为幂等键调用 transaction-service 过账，"
                    + "成功置 SETTLED 并保存 transactionBatchId；下游不可用 503 且状态不变。")
    @PostMapping("/{paymentId}/settle")
    public PaymentOrderResponse settle(@PathVariable String paymentId) {
        return PaymentOrderResponse.from(paymentOrderService.settle(paymentId));
    }

    @Operation(summary = "退汇",
            description = "允许 ACCEPTED / SETTLED；已清算先调用 transaction-service 冲正，再置 RETURNED；重复调用幂等。")
    @PostMapping("/{paymentId}/return")
    public PaymentOrderResponse returnPayment(@PathVariable String paymentId) {
        return PaymentOrderResponse.from(paymentOrderService.returnPayment(paymentId));
    }
}
