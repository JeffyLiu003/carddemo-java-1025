package com.carddemo.search.application;

import com.carddemo.search.domain.exception.InvalidRequestException;
import com.carddemo.search.domain.exception.RemoteServiceException;
import com.carddemo.search.domain.model.CustomerSummary;
import com.carddemo.search.domain.model.SearchItem;
import com.carddemo.search.domain.model.SearchResult;
import com.carddemo.search.domain.model.SearchType;
import com.carddemo.search.domain.model.TransactionSummary;
import com.carddemo.search.infrastructure.client.CustomerClient;
import com.carddemo.search.infrastructure.client.TransactionClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 统一搜索（docs/design/16-channel-and-quality.md §3）。
 *
 * <p>没有本地索引：客户由 customer-service 全量拉取后按客户号/姓名本地包含过滤；
 * 交易只在查询串是 11 位账户号或 16 位卡号时才查询 transaction-service。
 *
 * <p>失败语义：ALL 时单个下游失败只降级（对应类型为空 + degraded）；查询类型明确
 * （CUSTOMER/TRANSACTION）时该下游失败直接 503；ALL 时两个下游都失败也返回 503。
 */
@Service
public class SearchService {

    private static final String CUSTOMER_SERVICE = "customer-service";
    private static final String TRANSACTION_SERVICE = "transaction-service";

    private static final Pattern ACCOUNT_ID = Pattern.compile("\\d{11}");
    private static final Pattern CARD_NUMBER = Pattern.compile("\\d{16}");

    private final CustomerClient customers;
    private final TransactionClient transactions;

    public SearchService(CustomerClient customers, TransactionClient transactions) {
        this.customers = customers;
        this.transactions = transactions;
    }

    public SearchResult search(String query, String type, int limit) {
        SearchType searchType = SearchType.parse(type);
        String q = query == null ? "" : query.trim();
        if (q.isEmpty()) {
            throw new InvalidRequestException("VALIDATION_FAILED", "Search query q must be supplied");
        }

        List<SearchItem> items = new ArrayList<>();
        List<String> degraded = new ArrayList<>(2);
        boolean customerFailed = false;
        boolean transactionFailed = false;

        if (searchType.includesCustomers()) {
            try {
                items.addAll(customerItems(q));
            } catch (RemoteServiceException ex) {
                customerFailed = true;
                degraded.add(CUSTOMER_SERVICE);
            }
        }
        if (searchType.includesTransactions() && isTransactionQuery(q)) {
            try {
                items.addAll(transactionItems(q));
            } catch (RemoteServiceException ex) {
                transactionFailed = true;
                degraded.add(TRANSACTION_SERVICE);
            }
        }

        boolean dependencyLost = switch (searchType) {
            case CUSTOMER -> customerFailed;
            case TRANSACTION -> transactionFailed;
            case ALL -> customerFailed && transactionFailed;
        };
        if (dependencyLost) {
            throw new RemoteServiceException(
                    "Search dependency is unavailable: " + String.join(", ", degraded));
        }

        long total = items.size();
        List<SearchItem> limited = total > limit ? List.copyOf(items.subList(0, limit)) : List.copyOf(items);
        return new SearchResult(limited, total, List.copyOf(degraded));
    }

    private List<SearchItem> customerItems(String query) {
        return customers.listCustomers().stream()
                .filter(customer -> matches(customer, query))
                .map(SearchService::toItem)
                .toList();
    }

    private List<SearchItem> transactionItems(String query) {
        String accountId = ACCOUNT_ID.matcher(query).matches() ? query : null;
        String cardNumber = CARD_NUMBER.matcher(query).matches() ? query : null;
        return transactions.search(null, cardNumber, accountId).stream()
                .map(SearchService::toItem)
                .toList();
    }

    private static boolean isTransactionQuery(String query) {
        return ACCOUNT_ID.matcher(query).matches() || CARD_NUMBER.matcher(query).matches();
    }

    private static boolean matches(CustomerSummary customer, String query) {
        return contains(customer.customerId(), query)
                || contains(customer.firstName(), query)
                || contains(customer.middleName(), query)
                || contains(customer.lastName(), query);
    }

    private static boolean contains(String value, String query) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT));
    }

    private static SearchItem toItem(CustomerSummary customer) {
        return new SearchItem(SearchType.CUSTOMER.name(), customer.customerId(), fullName(customer),
                customer.customerId(), null, customer.ssnMasked());
    }

    private static SearchItem toItem(TransactionSummary transaction) {
        String title = transaction.description() == null || transaction.description().isBlank()
                ? transaction.transactionId()
                : transaction.description();
        return new SearchItem(SearchType.TRANSACTION.name(), transaction.transactionId(), title,
                transaction.accountId(), transaction.amount(), transaction.cardNumber());
    }

    private static String fullName(CustomerSummary customer) {
        return Stream.of(customer.firstName(), customer.middleName(), customer.lastName())
                .filter(part -> part != null && !part.isBlank())
                .collect(Collectors.joining(" "));
    }
}
