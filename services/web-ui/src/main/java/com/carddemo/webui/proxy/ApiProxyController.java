package com.carddemo.webui.proxy;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import jakarta.servlet.http.HttpServletRequest;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 同源 API 代理：浏览器只访问 8080，由本服务按路径前缀转发到对应微服务。
 * 这样前端不需要 CORS，也不需要知道每个微服务的端口。
 */
@RestController
public class ApiProxyController {

    private static final String ROUTE_NOT_FOUND =
            "{\"code\":\"ROUTE_NOT_FOUND\",\"message\":\"No upstream service is mapped to this path\"}";
    private static final String UPSTREAM_UNAVAILABLE =
            "{\"code\":\"DEPENDENCY_UNAVAILABLE\",\"message\":\"Upstream service is unavailable\"}";

    private final Map<String, String> routes = new LinkedHashMap<>();
    private final RestClient restClient;

    public ApiProxyController(
            @Value("${proxy.iam-service-url}") String iamServiceUrl,
            @Value("${proxy.customer-service-url}") String customerServiceUrl,
            @Value("${proxy.card-service-url}") String cardServiceUrl,
            @Value("${proxy.account-service-url}") String accountServiceUrl,
            @Value("${proxy.transaction-service-url}") String transactionServiceUrl,
            @Value("${proxy.authorization-service-url}") String authorizationServiceUrl,
            @Value("${proxy.marketing-service-url}") String marketingServiceUrl,
            @Value("${proxy.ledger-service-url}") String ledgerServiceUrl,
            @Value("${proxy.reconciliation-service-url}") String reconciliationServiceUrl,
            @Value("${proxy.payment-service-url}") String paymentServiceUrl,
            @Value("${proxy.billing-service-url}") String billingServiceUrl,
            @Value("${proxy.limit-service-url}") String limitServiceUrl,
            @Value("${proxy.fx-service-url}") String fxServiceUrl,
            @Value("${proxy.bff-service-url}") String bffServiceUrl,
            @Value("${proxy.search-service-url}") String searchServiceUrl,
            @Value("${proxy.loan-service-url}") String loanServiceUrl,
            @Value("${proxy.fraud-service-url}") String fraudServiceUrl,
            @Value("${proxy.aml-service-url}") String amlServiceUrl,
            @Value("${proxy.dispute-service-url}") String disputeServiceUrl,
            @Value("${proxy.collections-service-url}") String collectionsServiceUrl,
            @Value("${proxy.batch-service-url}") String batchServiceUrl,
            @Value("${proxy.reporting-service-url}") String reportingServiceUrl,
            @Value("${proxy.notification-service-url}") String notificationServiceUrl,
            @Value("${proxy.document-service-url}") String documentServiceUrl,
            @Value("${proxy.workflow-service-url}") String workflowServiceUrl,
            @Value("${proxy.audit-service-url}") String auditServiceUrl,
            @Value("${proxy.reference-service-url}") String referenceServiceUrl) {
        routes.put("/api/v1/auth", iamServiceUrl);
        routes.put("/api/v1/users", iamServiceUrl);
        routes.put("/api/v1/customers", customerServiceUrl);
        routes.put("/api/v1/cards", cardServiceUrl);
        routes.put("/api/v1/accounts", accountServiceUrl);
        routes.put("/api/v1/transactions", transactionServiceUrl);
        routes.put("/api/v1/postings", transactionServiceUrl);
        routes.put("/api/v1/bill-payments", transactionServiceUrl);
        routes.put("/api/v1/interest-runs", transactionServiceUrl);
        // 交易报表（transaction-service）比 reporting 的 /api/v1/reports 更具体，必须先匹配
        routes.put("/api/v1/reports/transactions", transactionServiceUrl);
        routes.put("/api/v1/transaction-types", transactionServiceUrl);
        routes.put("/api/v1/transaction-categories", transactionServiceUrl);
        routes.put("/api/v1/posting-types", transactionServiceUrl);
        routes.put("/api/v1/posting-batches", transactionServiceUrl);
        routes.put("/api/v1/authorizations", authorizationServiceUrl);
        routes.put("/api/v1/pending-authorizations", authorizationServiceUrl);
        routes.put("/api/v1/authorization-purges", authorizationServiceUrl);
        // 营销：高端卡分期返现活动、资格、返现评估与奖励
        routes.put("/api/v1/campaigns", marketingServiceUrl);
        routes.put("/api/v1/eligibilities", marketingServiceUrl);
        routes.put("/api/v1/reward-evaluations", marketingServiceUrl);
        routes.put("/api/v1/rewards", marketingServiceUrl);
        routes.put("/api/v1/marketing", marketingServiceUrl);
        routes.put("/api/v1/ledger-accounts", ledgerServiceUrl);
        routes.put("/api/v1/journal-entries", ledgerServiceUrl);
        routes.put("/api/v1/trial-balance", ledgerServiceUrl);
        routes.put("/api/v1/accounting-periods", ledgerServiceUrl);
        routes.put("/api/v1/reconciliation-runs", reconciliationServiceUrl);
        routes.put("/api/v1/reconciliation-items", reconciliationServiceUrl);
        routes.put("/api/v1/eod-runs", reconciliationServiceUrl);
        routes.put("/api/v1/payment-orders", paymentServiceUrl);
        routes.put("/api/v1/statements", billingServiceUrl);
        routes.put("/api/v1/limits", limitServiceUrl);
        routes.put("/api/v1/fx", fxServiceUrl);
        routes.put("/api/v1/bff", bffServiceUrl);
        routes.put("/api/v1/search", searchServiceUrl);
        routes.put("/api/v1/loan-applications", loanServiceUrl);
        routes.put("/api/v1/loans", loanServiceUrl);
        routes.put("/api/v1/fraud-evaluations", fraudServiceUrl);
        routes.put("/api/v1/fraud-alerts", fraudServiceUrl);
        routes.put("/api/v1/screenings", amlServiceUrl);
        routes.put("/api/v1/aml-alerts", amlServiceUrl);
        routes.put("/api/v1/disputes", disputeServiceUrl);
        routes.put("/api/v1/delinquency-cases", collectionsServiceUrl);
        routes.put("/api/v1/jobs", batchServiceUrl);
        routes.put("/api/v1/job-runs", batchServiceUrl);
        routes.put("/api/v1/reports", reportingServiceUrl);
        routes.put("/api/v1/report-runs", reportingServiceUrl);
        routes.put("/api/v1/notification-templates", notificationServiceUrl);
        routes.put("/api/v1/notifications", notificationServiceUrl);
        routes.put("/api/v1/documents", documentServiceUrl);
        routes.put("/api/v1/workflow-definitions", workflowServiceUrl);
        routes.put("/api/v1/workflow-tasks", workflowServiceUrl);
        routes.put("/api/v1/audit-events", auditServiceUrl);
        routes.put("/api/v1/reference", referenceServiceUrl);

        this.restClient = RestClient.builder()
                .requestFactory(requestFactory())
                .build();
    }

    @RequestMapping("/api/**")
    public ResponseEntity<byte[]> proxy(HttpServletRequest request,
                                       @RequestBody(required = false) byte[] body) {
        String target = resolveTarget(request.getRequestURI());
        if (target == null) {
            return json(HttpStatus.NOT_FOUND, ROUTE_NOT_FOUND);
        }

        String query = request.getQueryString();
        String url = target + request.getRequestURI() + (query == null ? "" : "?" + query);

        try {
            RestClient.RequestBodySpec spec = restClient
                    .method(HttpMethod.valueOf(request.getMethod()))
                    .uri(URI.create(url));
            copyHeader(request, spec, HttpHeaders.CONTENT_TYPE);
            copyHeader(request, spec, HttpHeaders.ACCEPT);
            copyHeader(request, spec, HttpHeaders.ACCEPT_LANGUAGE);
            copyHeader(request, spec, HttpHeaders.AUTHORIZATION);
            copyHeader(request, spec, "X-User-Id");
            if (body != null && body.length > 0) {
                spec.body(body);
            }
            return spec.exchange((clientRequest, clientResponse) -> {
                HttpHeaders headers = new HttpHeaders();
                String contentType = clientResponse.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE);
                if (contentType != null) {
                    headers.set(HttpHeaders.CONTENT_TYPE, contentType);
                }
                byte[] payload = clientResponse.getBody().readAllBytes();
                return new ResponseEntity<>(payload, headers, clientResponse.getStatusCode());
            });
        } catch (ResourceAccessException ex) {
            return json(HttpStatus.SERVICE_UNAVAILABLE, UPSTREAM_UNAVAILABLE);
        } catch (RestClientException ex) {
            return json(HttpStatus.BAD_GATEWAY,
                    "{\"code\":\"UPSTREAM_ERROR\",\"message\":\"Upstream request failed\"}");
        }
    }

    String resolveTarget(String path) {
        for (Map.Entry<String, String> route : routes.entrySet()) {
            if (path.equals(route.getKey()) || path.startsWith(route.getKey() + "/")) {
                return route.getValue();
            }
        }
        return null;
    }

    private static org.springframework.http.client.JdkClientHttpRequestFactory requestFactory() {
        org.springframework.http.client.JdkClientHttpRequestFactory factory =
                new org.springframework.http.client.JdkClientHttpRequestFactory();
        factory.setReadTimeout(Duration.ofSeconds(30));
        return factory;
    }

    private static void copyHeader(HttpServletRequest request,
                                   RestClient.RequestBodySpec spec,
                                   String name) {
        Enumeration<String> values = request.getHeaders(name);
        while (values.hasMoreElements()) {
            spec.header(name, values.nextElement());
        }
    }

    private static ResponseEntity<byte[]> json(HttpStatus status, String body) {
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body.getBytes(StandardCharsets.UTF_8));
    }
}
