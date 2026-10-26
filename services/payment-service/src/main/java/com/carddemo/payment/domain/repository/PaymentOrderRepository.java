package com.carddemo.payment.domain.repository;

import com.carddemo.payment.domain.model.PaymentOrder;

import java.util.Optional;

/**
 * 支付指令仓储端口。实现见 infrastructure.persistence.PaymentOrderJpaRepository。
 */
public interface PaymentOrderRepository {

    PaymentOrder saveOrder(PaymentOrder order);

    Optional<PaymentOrder> findByPaymentId(String paymentId);

    Optional<PaymentOrder> findByIdempotencyKey(String idempotencyKey);

    PageResult<PaymentOrder> search(PaymentOrderFilter filter, int page, int size);
}
