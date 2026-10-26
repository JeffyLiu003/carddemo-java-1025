package com.carddemo.payment.interfaces.rest.dto;

import com.carddemo.payment.domain.model.PaymentOrder;
import com.carddemo.payment.domain.repository.PageResult;

import java.util.List;

public record PaymentOrderListResponse(List<PaymentOrderResponse> items, int page, int size, long total) {

    public static PaymentOrderListResponse from(PageResult<PaymentOrder> result, int page, int size) {
        return new PaymentOrderListResponse(
                result.items().stream().map(PaymentOrderResponse::from).toList(),
                page,
                size,
                result.total());
    }
}
