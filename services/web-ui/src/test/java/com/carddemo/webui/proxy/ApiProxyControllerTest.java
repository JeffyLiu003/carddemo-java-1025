package com.carddemo.webui.proxy;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class ApiProxyControllerTest {

    private final ApiProxyController controller = new ApiProxyController(
            "http://iam:8081", "http://customer:8082", "http://card:8083",
            "http://account:8084", "http://transaction:8085", "http://authorization:8086",
            "http://ledger:8088", "http://reconciliation:8089",
            "http://payment:8091", "http://billing:8092", "http://limit:8093", "http://fx:8094",
            "http://bff:8107", "http://search:8108", "http://loan:8095", "http://fraud:8096",
            "http://aml:8097", "http://dispute:8098", "http://collections:8099", "http://batch:8100",
            "http://reporting:8101", "http://notification:8102", "http://document:8103",
            "http://workflow:8104", "http://audit:8105", "http://reference:8106");

    @Test
    void routesByContextPrefix() {
        assertThat(controller.resolveTarget("/api/v1/auth/login")).isEqualTo("http://iam:8081");
        assertThat(controller.resolveTarget("/api/v1/users/ADMIN001")).isEqualTo("http://iam:8081");
        assertThat(controller.resolveTarget("/api/v1/customers/000000001")).isEqualTo("http://customer:8082");
        assertThat(controller.resolveTarget("/api/v1/cards/by-account/00000000001")).isEqualTo("http://card:8083");
        assertThat(controller.resolveTarget("/api/v1/accounts/00000000001")).isEqualTo("http://account:8084");
        assertThat(controller.resolveTarget("/api/v1/transactions/0000000000000001"))
                .isEqualTo("http://transaction:8085");
        assertThat(controller.resolveTarget("/api/v1/transaction-types"))
                .isEqualTo("http://transaction:8085");
        assertThat(controller.resolveTarget("/api/v1/reports/transactions"))
                .isEqualTo("http://transaction:8085");
        assertThat(controller.resolveTarget("/api/v1/bill-payments")).isEqualTo("http://transaction:8085");
        assertThat(controller.resolveTarget("/api/v1/pending-authorizations/00000000001"))
                .isEqualTo("http://authorization:8086");
        assertThat(controller.resolveTarget("/api/v1/authorization-purges"))
                .isEqualTo("http://authorization:8086");
        assertThat(controller.resolveTarget("/api/v1/posting-types")).isEqualTo("http://transaction:8085");
        assertThat(controller.resolveTarget("/api/v1/posting-types/CARD_PURCHASE"))
                .isEqualTo("http://transaction:8085");
        assertThat(controller.resolveTarget("/api/v1/posting-batches")).isEqualTo("http://transaction:8085");
        assertThat(controller.resolveTarget("/api/v1/posting-batches/b-1/reversal"))
                .isEqualTo("http://transaction:8085");
        assertThat(controller.resolveTarget("/api/v1/ledger-accounts")).isEqualTo("http://ledger:8088");
        assertThat(controller.resolveTarget("/api/v1/ledger-accounts/1100")).isEqualTo("http://ledger:8088");
        assertThat(controller.resolveTarget("/api/v1/journal-entries")).isEqualTo("http://ledger:8088");
        assertThat(controller.resolveTarget("/api/v1/trial-balance")).isEqualTo("http://ledger:8088");
        assertThat(controller.resolveTarget("/api/v1/accounting-periods/2026-10/close"))
                .isEqualTo("http://ledger:8088");
        assertThat(controller.resolveTarget("/api/v1/reconciliation-runs"))
                .isEqualTo("http://reconciliation:8089");
        assertThat(controller.resolveTarget("/api/v1/reconciliation-runs/run-1"))
                .isEqualTo("http://reconciliation:8089");
        assertThat(controller.resolveTarget("/api/v1/reconciliation-runs/run-1/items"))
                .isEqualTo("http://reconciliation:8089");
        assertThat(controller.resolveTarget("/api/v1/reconciliation-items/item-1/resolve"))
                .isEqualTo("http://reconciliation:8089");
        assertThat(controller.resolveTarget("/api/v1/eod-runs")).isEqualTo("http://reconciliation:8089");
        assertThat(controller.resolveTarget("/api/v1/eod-runs/eod-1"))
                .isEqualTo("http://reconciliation:8089");
        assertThat(controller.resolveTarget("/api/v1/payment-orders")).isEqualTo("http://payment:8091");
        assertThat(controller.resolveTarget("/api/v1/payment-orders/p-1/accept"))
                .isEqualTo("http://payment:8091");
        assertThat(controller.resolveTarget("/api/v1/statements")).isEqualTo("http://billing:8092");
        assertThat(controller.resolveTarget("/api/v1/statements/generate")).isEqualTo("http://billing:8092");
        assertThat(controller.resolveTarget("/api/v1/statements/s-1/pay")).isEqualTo("http://billing:8092");
        assertThat(controller.resolveTarget("/api/v1/limits")).isEqualTo("http://limit:8093");
        assertThat(controller.resolveTarget("/api/v1/limits/check")).isEqualTo("http://limit:8093");
        assertThat(controller.resolveTarget("/api/v1/limits/l-1/usage")).isEqualTo("http://limit:8093");
        assertThat(controller.resolveTarget("/api/v1/fx/rates")).isEqualTo("http://fx:8094");
        assertThat(controller.resolveTarget("/api/v1/fx/quotes/q-1/execute")).isEqualTo("http://fx:8094");
        assertThat(controller.resolveTarget("/api/v1/fx/conversions")).isEqualTo("http://fx:8094");
    }

    @Test
    void routesExtendedServicesByContextPrefix() {
        assertThat(controller.resolveTarget("/api/v1/bff/customer-overview"))
                .isEqualTo("http://bff:8107");
        assertThat(controller.resolveTarget("/api/v1/search")).isEqualTo("http://search:8108");
        assertThat(controller.resolveTarget("/api/v1/loan-applications"))
                .isEqualTo("http://loan:8095");
        assertThat(controller.resolveTarget("/api/v1/loan-applications/a-1/approve"))
                .isEqualTo("http://loan:8095");
        assertThat(controller.resolveTarget("/api/v1/loans/l-1/disburse")).isEqualTo("http://loan:8095");
        assertThat(controller.resolveTarget("/api/v1/fraud-evaluations")).isEqualTo("http://fraud:8096");
        assertThat(controller.resolveTarget("/api/v1/fraud-alerts/al-1/confirm"))
                .isEqualTo("http://fraud:8096");
        assertThat(controller.resolveTarget("/api/v1/screenings")).isEqualTo("http://aml:8097");
        assertThat(controller.resolveTarget("/api/v1/aml-alerts/al-1/escalate"))
                .isEqualTo("http://aml:8097");
        assertThat(controller.resolveTarget("/api/v1/disputes/d-1/provisional-credit"))
                .isEqualTo("http://dispute:8098");
        assertThat(controller.resolveTarget("/api/v1/delinquency-cases/c-1/promise-to-pay"))
                .isEqualTo("http://collections:8099");
        assertThat(controller.resolveTarget("/api/v1/jobs")).isEqualTo("http://batch:8100");
        assertThat(controller.resolveTarget("/api/v1/job-runs")).isEqualTo("http://batch:8100");
        assertThat(controller.resolveTarget("/api/v1/reports")).isEqualTo("http://reporting:8101");
        assertThat(controller.resolveTarget("/api/v1/report-runs/run-1"))
                .isEqualTo("http://reporting:8101");
        assertThat(controller.resolveTarget("/api/v1/notification-templates"))
                .isEqualTo("http://notification:8102");
        assertThat(controller.resolveTarget("/api/v1/notifications/n-1/send"))
                .isEqualTo("http://notification:8102");
        assertThat(controller.resolveTarget("/api/v1/documents/d-1/content"))
                .isEqualTo("http://document:8103");
        assertThat(controller.resolveTarget("/api/v1/workflow-definitions"))
                .isEqualTo("http://workflow:8104");
        assertThat(controller.resolveTarget("/api/v1/workflow-tasks/t-1/approve"))
                .isEqualTo("http://workflow:8104");
        assertThat(controller.resolveTarget("/api/v1/audit-events")).isEqualTo("http://audit:8105");
        assertThat(controller.resolveTarget("/api/v1/reference/CURRENCY"))
                .isEqualTo("http://reference:8106");
        assertThat(controller.resolveTarget("/api/v1/reference/i-1")).isEqualTo("http://reference:8106");
    }

    @Test
    void keepsTransactionReportAheadOfReportingService() {
        assertThat(controller.resolveTarget("/api/v1/reports/transactions"))
                .isEqualTo("http://transaction:8085");
        assertThat(controller.resolveTarget("/api/v1/reports")).isEqualTo("http://reporting:8101");
        assertThat(controller.resolveTarget("/api/v1/report-run")).isNull();
    }

    @Test
    void keepsPostingsAndPostingTypesApart() {
        assertThat(controller.resolveTarget("/api/v1/postings")).isEqualTo("http://transaction:8085");
        assertThat(controller.resolveTarget("/api/v1/postings/123")).isEqualTo("http://transaction:8085");
        assertThat(controller.resolveTarget("/api/v1/posting-types")).isEqualTo("http://transaction:8085");
        assertThat(controller.resolveTarget("/api/v1/posting-batch")).isNull();
    }

    @Test
    void doesNotMatchLookalikePaths() {
        assertThat(controller.resolveTarget("/api/v1/accountszzz")).isNull();
        assertThat(controller.resolveTarget("/api/v1/user")).isNull();
        assertThat(controller.resolveTarget("/api/v2/accounts")).isNull();
        assertThat(controller.resolveTarget("/api/v1/reconciliation-run")).isNull();
        assertThat(controller.resolveTarget("/api/v1/eod-run")).isNull();
        assertThat(controller.resolveTarget("/api/v1/payment-order")).isNull();
        assertThat(controller.resolveTarget("/api/v1/statement")).isNull();
        assertThat(controller.resolveTarget("/api/v1/limit")).isNull();
        assertThat(controller.resolveTarget("/api/v1/fx-rates")).isNull();
        assertThat(controller.resolveTarget("/api/v1/loan-application")).isNull();
        assertThat(controller.resolveTarget("/api/v1/loan")).isNull();
        assertThat(controller.resolveTarget("/api/v1/fraud-alert")).isNull();
        assertThat(controller.resolveTarget("/api/v1/screening")).isNull();
        assertThat(controller.resolveTarget("/api/v1/dispute")).isNull();
        assertThat(controller.resolveTarget("/api/v1/delinquency-case")).isNull();
        assertThat(controller.resolveTarget("/api/v1/job-run")).isNull();
        assertThat(controller.resolveTarget("/api/v1/notification-template")).isNull();
        assertThat(controller.resolveTarget("/api/v1/document")).isNull();
        assertThat(controller.resolveTarget("/api/v1/workflow-task")).isNull();
        assertThat(controller.resolveTarget("/api/v1/audit-event")).isNull();
        assertThat(controller.resolveTarget("/api/v1/bffx")).isNull();
    }
}
