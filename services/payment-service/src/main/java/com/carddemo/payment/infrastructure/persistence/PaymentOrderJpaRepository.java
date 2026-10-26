package com.carddemo.payment.infrastructure.persistence;

import com.carddemo.payment.domain.model.PaymentOrder;
import com.carddemo.payment.domain.model.PaymentStatus;
import com.carddemo.payment.domain.repository.PageResult;
import com.carddemo.payment.domain.repository.PaymentOrderFilter;
import com.carddemo.payment.domain.repository.PaymentOrderRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public interface PaymentOrderJpaRepository
        extends JpaRepository<PaymentOrder, String>, JpaSpecificationExecutor<PaymentOrder>,
        PaymentOrderRepository {

    @Override
    default PaymentOrder saveOrder(PaymentOrder order) {
        return saveAndFlush(order);
    }

    @Override
    default Optional<PaymentOrder> findByPaymentId(String paymentId) {
        return findById(paymentId);
    }

    @Override
    Optional<PaymentOrder> findByIdempotencyKey(String idempotencyKey);

    @Override
    default PageResult<PaymentOrder> search(PaymentOrderFilter filter, int page, int size) {
        Sort sort = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("paymentId"));
        Page<PaymentOrder> result = findAll(specification(filter), PageRequest.of(page, size, sort));
        return new PageResult<>(result.getContent(), result.getTotalElements());
    }

    private static Specification<PaymentOrder> specification(PaymentOrderFilter filter) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (filter != null && StringUtils.hasText(filter.status())) {
                predicates.add(builder.equal(root.get("status"), PaymentStatus.valueOf(filter.status())));
            }
            if (filter != null && StringUtils.hasText(filter.accountId())) {
                predicates.add(builder.equal(root.get("debtorAccountId"), filter.accountId()));
            }
            return builder.and(predicates.toArray(new Predicate[0]));
        };
    }
}
