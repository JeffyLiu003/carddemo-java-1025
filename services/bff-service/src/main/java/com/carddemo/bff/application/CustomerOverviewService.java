package com.carddemo.bff.application;

import com.carddemo.bff.domain.exception.RemoteServiceException;
import com.carddemo.bff.domain.model.AccountPosition;
import com.carddemo.bff.domain.model.CardSummary;
import com.carddemo.bff.domain.model.CustomerOverview;
import com.carddemo.bff.domain.model.CustomerProfile;
import com.carddemo.bff.domain.model.RecentTransaction;
import com.carddemo.bff.infrastructure.client.AccountClient;
import com.carddemo.bff.infrastructure.client.CardClient;
import com.carddemo.bff.infrastructure.client.CustomerClient;
import com.carddemo.bff.infrastructure.client.TransactionClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 客户 360 聚合（docs/design/16-channel-and-quality.md §2）。
 *
 * <p>账户额度是概览的骨架：account-service 404 → 404 ACCOUNT_NOT_FOUND，5xx/超时 → 503。
 * 其余下游失败只降级：对应节点为空（或 null），服务名记入 {@code degraded}，整体仍返回 200。
 *
 * <p>客户身份从第一张卡的 customerId 解析；账户无卡时客户与权益都为空。
 */
@Service
public class CustomerOverviewService {

    private static final String CARD_SERVICE = "card-service";
    private static final String CUSTOMER_SERVICE = "customer-service";
    private static final String TRANSACTION_SERVICE = "transaction-service";

    private static final int RECENT_TRANSACTION_SIZE = 5;

    private final AccountClient accounts;
    private final CardClient cards;
    private final CustomerClient customers;
    private final TransactionClient transactions;

    public CustomerOverviewService(AccountClient accounts, CardClient cards, CustomerClient customers,
                                   TransactionClient transactions) {
        this.accounts = accounts;
        this.cards = cards;
        this.customers = customers;
        this.transactions = transactions;
    }

    public CustomerOverview overview(String accountId) {
        AccountPosition position = accounts.creditPosition(accountId);
        List<String> degraded = new ArrayList<>(4);

        List<CardSummary> accountCards = degrade(CARD_SERVICE, degraded,
                () -> cards.listByAccount(accountId), List.of());
        String customerId = firstCustomerId(accountCards);
        CustomerProfile customer = loadCustomer(customerId, degraded);
        List<RecentTransaction> recentTransactions = degrade(TRANSACTION_SERVICE, degraded,
                () -> transactions.recentByAccount(accountId, RECENT_TRANSACTION_SIZE), List.of());

        return new CustomerOverview(accountId, position, accountCards, customer,
                recentTransactions, List.copyOf(degraded));
    }

    private CustomerProfile loadCustomer(String customerId, List<String> degraded) {
        if (customerId == null) {
            return null;
        }
        try {
            return customers.findById(customerId).orElse(null);
        } catch (RemoteServiceException ex) {
            degraded.add(CUSTOMER_SERVICE);
            return null;
        }
    }

    private static String firstCustomerId(List<CardSummary> cards) {
        return cards.stream()
                .map(CardSummary::customerId)
                .filter(customerId -> customerId != null && !customerId.isBlank())
                .findFirst()
                .orElse(null);
    }

    private static <T> T degrade(String service, List<String> degraded, Supplier<T> call, T fallback) {
        try {
            return call.get();
        } catch (RemoteServiceException ex) {
            degraded.add(service);
            return fallback;
        }
    }
}
