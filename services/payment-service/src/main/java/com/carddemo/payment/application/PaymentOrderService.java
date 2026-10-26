package com.carddemo.payment.application;

import com.carddemo.payment.domain.exception.BusinessRuleException;
import com.carddemo.payment.domain.exception.InvalidRequestException;
import com.carddemo.payment.domain.exception.NotFoundException;
import com.carddemo.payment.domain.exception.RemoteServiceException;
import com.carddemo.payment.domain.model.AccountSnapshot;
import com.carddemo.payment.domain.model.PaymentOrder;
import com.carddemo.payment.domain.model.PaymentStatus;
import com.carddemo.payment.domain.model.PaymentType;
import com.carddemo.payment.domain.repository.PageResult;
import com.carddemo.payment.domain.repository.PaymentOrderFilter;
import com.carddemo.payment.domain.repository.PaymentOrderRepository;
import com.carddemo.payment.domain.service.CreditorScreeningPolicy;
import com.carddemo.payment.infrastructure.client.AccountClient;
import com.carddemo.payment.infrastructure.client.TransactionClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * 支付指令用例（设计 §2.2）：受理 + 自动筛查、接受、清算过账、退汇冲正、查询。
 * <p>
 * 幂等约定：创建以请求 idempotencyKey 幂等；清算以 {@code payment:<paymentId>} 作为过账批次幂等键；
 * 重复 accept / settle / return 直接返回当前单据，不重复调用下游。
 * 下游不可用时抛 {@link RemoteServiceException}，事务回滚，支付单状态不变。
 */
@Service
@Transactional
public class PaymentOrderService {

    private static final String ACTIVE = "Y";
    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final String SETTLEMENT_IDEMPOTENCY_PREFIX = "payment:";

    private final PaymentOrderRepository paymentOrders;
    private final AccountClient accountClient;
    private final TransactionClient transactionClient;
    private final CreditorScreeningPolicy screeningPolicy;

    public PaymentOrderService(PaymentOrderRepository paymentOrders,
                               AccountClient accountClient,
                               TransactionClient transactionClient,
                               CreditorScreeningPolicy screeningPolicy) {
        this.paymentOrders = paymentOrders;
        this.accountClient = accountClient;
        this.transactionClient = transactionClient;
        this.screeningPolicy = screeningPolicy;
    }

    /**
     * 受理支付指令：幂等键必填 → 同键同请求返回原单 / 同键异请求 409 → 账户必须存在且有效 →
     * 自动筛查（通过 SCREENED，否则 REJECTED + reject_reason）。
     */
    public PaymentOrder create(CreatePaymentOrderCommand command) {
        String idempotencyKey = requireIdempotencyKey(command.idempotencyKey());
        String accountId = trimToNull(command.debtorAccountId());
        if (accountId == null) {
            throw new InvalidRequestException("ACCOUNT_REQUIRED", "Debtor account must be supplied.");
        }
        BigDecimal amount = PaymentOrder.requireAmount(command.amount());
        String currency = PaymentOrder.requireCurrency(command.currency());
        PaymentType paymentType = PaymentType.require(command.paymentType());
        String creditorName = trimToNull(command.creditorName());
        String creditorAccountNumber = trimToNull(command.creditorAccountNumber());
        String creditorBankCode = trimToNull(command.creditorBankCode());

        Optional<PaymentOrder> duplicate = paymentOrders.findByIdempotencyKey(idempotencyKey);
        if (duplicate.isPresent()) {
            return existingOrder(duplicate.get(), accountId, creditorName, creditorAccountNumber,
                    creditorBankCode, amount, currency, paymentType);
        }

        AccountSnapshot account = accountClient.findAccount(accountId)
                .orElseThrow(() -> new NotFoundException("ACCOUNT_NOT_FOUND",
                        "Account " + accountId + " not found"));
        if (!ACTIVE.equalsIgnoreCase(account.activeStatus())) {
            throw new BusinessRuleException("ACCOUNT_INACTIVE", "Account " + accountId + " is inactive.");
        }

        LocalDateTime now = LocalDateTime.now();
        PaymentOrder order = PaymentOrder.receive(UUID.randomUUID().toString(), idempotencyKey,
                account.accountId(), creditorName, creditorAccountNumber, creditorBankCode, amount, currency,
                paymentType, trimToNull(command.reference()), trimToNull(command.description()), now);
        if (screeningPolicy.isAcceptable(creditorName)) {
            order.screen(now);
        } else {
            order.reject(screeningPolicy.rejectReason(creditorName), now);
        }
        return paymentOrders.saveOrder(order);
    }

    @Transactional(readOnly = true)
    public PaymentOrder get(String paymentId) {
        return paymentOrders.findByPaymentId(paymentId)
                .orElseThrow(() -> new NotFoundException("PAYMENT_NOT_FOUND",
                        "Payment order " + paymentId + " not found"));
    }

    @Transactional(readOnly = true)
    public PageResult<PaymentOrder> list(String status, String accountId, int page, int size) {
        PaymentOrderFilter filter = new PaymentOrderFilter(requireStatusFilter(status),
                trimToNull(accountId));
        return paymentOrders.search(filter, Math.max(page, 0), size <= 0 ? DEFAULT_PAGE_SIZE : size);
    }

    /**
     * 人工接受：仅 SCREENED；已 ACCEPTED 直接返回（幂等）。
     */
    public PaymentOrder accept(String paymentId) {
        PaymentOrder order = get(paymentId);
        if (order.getStatus() == PaymentStatus.ACCEPTED) {
            return order;
        }
        order.accept(LocalDateTime.now());
        return paymentOrders.saveOrder(order);
    }

    /**
     * 清算：仅 ACCEPTED；以 payment:&lt;paymentId&gt; 幂等键调 transaction-service 过账，
     * 成功保存 transactionBatchId 并置 SETTLED；已 SETTLED 直接返回（幂等）。
     */
    public PaymentOrder settle(String paymentId) {
        PaymentOrder order = get(paymentId);
        if (order.getStatus() == PaymentStatus.SETTLED) {
            return order;
        }
        if (order.getStatus() != PaymentStatus.ACCEPTED) {
            throw new BusinessRuleException("PAYMENT_NOT_ACCEPTED",
                    "Payment " + paymentId + " can only be settled from ACCEPTED (current "
                            + order.getStatus() + ").");
        }
        String batchId = transactionClient.postPayment(settlementIdempotencyKey(order),
                order.getDebtorAccountId(), order.getAmount(), order.getCurrency(),
                postingDescription(order));
        order.settle(batchId, LocalDateTime.now());
        return paymentOrders.saveOrder(order);
    }

    /**
     * 退汇：允许 ACCEPTED / SETTLED；已清算先调用 transaction-service 冲正，再置 RETURNED；
     * 已 RETURNED 直接返回（幂等）。
     */
    public PaymentOrder returnPayment(String paymentId) {
        PaymentOrder order = get(paymentId);
        if (order.getStatus() == PaymentStatus.RETURNED) {
            return order;
        }
        if (order.getStatus() != PaymentStatus.ACCEPTED && order.getStatus() != PaymentStatus.SETTLED) {
            throw new BusinessRuleException("PAYMENT_NOT_RETURNABLE",
                    "Payment " + paymentId + " can only be returned from ACCEPTED or SETTLED (current "
                            + order.getStatus() + ").");
        }
        if (order.getStatus() == PaymentStatus.SETTLED && order.getTransactionBatchId() != null) {
            transactionClient.reverseBatch(order.getTransactionBatchId());
        }
        order.returnPayment(LocalDateTime.now());
        return paymentOrders.saveOrder(order);
    }

    private String settlementIdempotencyKey(PaymentOrder order) {
        return SETTLEMENT_IDEMPOTENCY_PREFIX + order.getPaymentId();
    }

    private static String postingDescription(PaymentOrder order) {
        if (order.getDescription() != null && !order.getDescription().isBlank()) {
            return order.getDescription();
        }
        String reference = order.getReference();
        if (reference != null && !reference.isBlank()) {
            return reference;
        }
        return "Payment " + order.getPaymentId();
    }

    private PaymentOrder existingOrder(PaymentOrder existing, String accountId, String creditorName,
                                       String creditorAccountNumber, String creditorBankCode,
                                       BigDecimal amount, String currency, PaymentType paymentType) {
        boolean sameRequest = Objects.equals(existing.getDebtorAccountId(), accountId)
                && Objects.equals(existing.getCreditorName(), creditorName)
                && Objects.equals(existing.getCreditorAccountNumber(), creditorAccountNumber)
                && Objects.equals(existing.getCreditorBankCode(), creditorBankCode)
                && existing.getAmount().compareTo(amount) == 0
                && existing.getCurrency().equals(currency)
                && existing.getPaymentType() == paymentType;
        if (!sameRequest) {
            throw new BusinessRuleException("IDEMPOTENCY_CONFLICT",
                    "Idempotency key " + existing.getIdempotencyKey()
                            + " was already used with a different payment request.");
        }
        return existing;
    }

    private static String requireIdempotencyKey(String idempotencyKey) {
        String key = trimToNull(idempotencyKey);
        if (key == null) {
            throw new InvalidRequestException("IDEMPOTENCY_KEY_REQUIRED", "Idempotency key must be supplied.");
        }
        return key;
    }

    private static String requireStatusFilter(String status) {
        String normalized = trimToNull(status);
        if (normalized == null) {
            return null;
        }
        for (PaymentStatus candidate : PaymentStatus.values()) {
            if (candidate.name().equalsIgnoreCase(normalized)) {
                return candidate.name();
            }
        }
        throw new InvalidRequestException("INVALID_PAYMENT_STATUS",
                "Status filter must be one of RECEIVED, SCREENED, ACCEPTED, SETTLED, RETURNED, REJECTED.");
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
