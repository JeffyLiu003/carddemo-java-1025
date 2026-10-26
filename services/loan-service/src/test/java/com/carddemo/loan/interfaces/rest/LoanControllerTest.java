package com.carddemo.loan.interfaces.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 放款 / 详情 / 还款计划 / 还款端到端用例（设计 14 §2.2）。
 * <p>
 * 3 期 1000.00 @12% 的等额本息计划：340.02 / 340.02 / 340.03，合计 1020.07。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class LoanControllerTest {

    private static final String PRINCIPAL = "1000.00";
    private static final String ANNUAL_RATE = "0.1200";
    private static final int TERM_MONTHS = 3;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void disbursesApprovedLoan() throws Exception {
        String loanId = approvedLoan();

        mockMvc.perform(post("/api/v1/loans/{loanId}/disburse", loanId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loanId").value(loanId))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.disbursedAt").isNotEmpty());

        String applicationId = jdbcTemplate.queryForObject(
                "select application_id from loan where loan_id = ?", String.class, loanId);
        mockMvc.perform(get("/api/v1/loan-applications/{id}", applicationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISBURSED"));
    }

    @Test
    void disbursesTwiceReturns409() throws Exception {
        String loanId = approvedLoan();
        mockMvc.perform(post("/api/v1/loans/{loanId}/disburse", loanId))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/loans/{loanId}/disburse", loanId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LOAN_ALREADY_DISBURSED"));
    }

    @Test
    void repayBeforeDisbursementReturns409() throws Exception {
        String loanId = approvedLoan();

        mockMvc.perform(post("/api/v1/loans/{loanId}/repayments", loanId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":340.02}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LOAN_NOT_DISBURSED"));
    }

    @Test
    void repaysFirstInstallmentAndReducesOutstandingPrincipal() throws Exception {
        String loanId = disbursedLoan();

        mockMvc.perform(post("/api/v1/loans/{loanId}/repayments", loanId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":340.02}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loanId").value(loanId))
                .andExpect(jsonPath("$.amount").value(340.02))
                .andExpect(jsonPath("$.paidInstallments.length()").value(1))
                .andExpect(jsonPath("$.paidInstallments[0]").value(1))
                .andExpect(jsonPath("$.outstandingPrincipal").value(669.98))
                .andExpect(jsonPath("$.loanStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.schedule[0].status").value("PAID"))
                .andExpect(jsonPath("$.schedule[0].paidAt").isNotEmpty())
                .andExpect(jsonPath("$.schedule[1].status").value("PENDING"))
                .andExpect(jsonPath("$.schedule[2].status").value("PENDING"));
    }

    @Test
    void repayOfArbitraryAmountReturns409AndKeepsBalance() throws Exception {
        String loanId = disbursedLoan();

        mockMvc.perform(post("/api/v1/loans/{loanId}/repayments", loanId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":100.00}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_REPAYMENT_AMOUNT"));

        mockMvc.perform(get("/api/v1/loans/{loanId}", loanId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outstandingPrincipal").value(1000.00));
    }

    @Test
    void repayWithoutAmountReturns400() throws Exception {
        String loanId = disbursedLoan();

        mockMvc.perform(post("/api/v1/loans/{loanId}/repayments", loanId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void repaysRemainingBalanceAndClosesLoan() throws Exception {
        String loanId = disbursedLoan();
        mockMvc.perform(post("/api/v1/loans/{loanId}/repayments", loanId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":340.02}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/loans/{loanId}/repayments", loanId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":680.05}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paidInstallments.length()").value(2))
                .andExpect(jsonPath("$.outstandingPrincipal").value(0.00))
                .andExpect(jsonPath("$.loanStatus").value("CLOSED"))
                .andExpect(jsonPath("$.schedule[1].status").value("PAID"))
                .andExpect(jsonPath("$.schedule[2].status").value("PAID"));

        String applicationId = jdbcTemplate.queryForObject(
                "select application_id from loan where loan_id = ?", String.class, loanId);
        mockMvc.perform(get("/api/v1/loan-applications/{id}", applicationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"));
    }

    @Test
    void repayClosedLoanReturns409() throws Exception {
        String loanId = disbursedLoan();
        mockMvc.perform(post("/api/v1/loans/{loanId}/repayments", loanId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":1020.07}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loanStatus").value("CLOSED"));

        mockMvc.perform(post("/api/v1/loans/{loanId}/repayments", loanId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":340.02}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("LOAN_ALREADY_CLOSED"));
    }

    @Test
    void returnsLoanDetailAndSchedule() throws Exception {
        String loanId = disbursedLoan();

        mockMvc.perform(get("/api/v1/loans/{loanId}", loanId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.principal").value(1000.00))
                .andExpect(jsonPath("$.outstandingPrincipal").value(1000.00))
                .andExpect(jsonPath("$.termMonths").value(3))
                .andExpect(jsonPath("$.annualRate").value(0.1200))
                .andExpect(jsonPath("$.customerId").value("00000000020"))
                .andExpect(jsonPath("$.disbursedAt").isNotEmpty());

        mockMvc.perform(get("/api/v1/loans/{loanId}/schedule", loanId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loanId").value(loanId))
                .andExpect(jsonPath("$.items.length()").value(3))
                .andExpect(jsonPath("$.items[0].installmentNo").value(1))
                .andExpect(jsonPath("$.items[2].installmentNo").value(3))
                .andExpect(jsonPath("$.items[0].totalDue").value(340.02))
                .andExpect(jsonPath("$.items[0].status").value("PENDING"));
    }

    @Test
    void unknownLoanReturns404() throws Exception {
        mockMvc.perform(get("/api/v1/loans/{loanId}", "unknown-loan"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LOAN_NOT_FOUND"));

        mockMvc.perform(get("/api/v1/loans/{loanId}/schedule", "unknown-loan"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LOAN_NOT_FOUND"));

        mockMvc.perform(post("/api/v1/loans/{loanId}/disburse", "unknown-loan"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LOAN_NOT_FOUND"));

        mockMvc.perform(post("/api/v1/loans/{loanId}/repayments", "unknown-loan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":340.02}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LOAN_NOT_FOUND"));
    }

    @Test
    void scheduleAmountsSumToPrincipal() throws Exception {
        String loanId = approvedLoan("12000.00", 12, "0.1800");

        MvcResult result = mockMvc.perform(get("/api/v1/loans/{loanId}/schedule", loanId))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode items = body(result).get("items");
        BigDecimal principalSum = BigDecimal.ZERO;
        for (JsonNode item : items) {
            principalSum = principalSum.add(item.get("principalDue").decimalValue());
            assertThat(item.get("totalDue").decimalValue())
                    .isEqualByComparingTo(item.get("principalDue").decimalValue()
                            .add(item.get("interestDue").decimalValue()));
        }
        assertThat(principalSum).isEqualByComparingTo("12000.00");
    }

    private String approvedLoan() throws Exception {
        return approvedLoan(PRINCIPAL, TERM_MONTHS, ANNUAL_RATE);
    }

    private String approvedLoan(String principal, int termMonths, String annualRate) throws Exception {
        MvcResult submitted = mockMvc.perform(post("/api/v1/loan-applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId":"00000000020","productCode":"PERSONAL","principal":%s,
                                 "termMonths":%d,"annualRate":%s}
                                """.formatted(principal, termMonths, annualRate)))
                .andExpect(status().isCreated())
                .andReturn();
        String applicationId = body(submitted).get("applicationId").asText();

        MvcResult approved = mockMvc.perform(
                        post("/api/v1/loan-applications/{id}/approve", applicationId))
                .andExpect(status().isOk())
                .andReturn();
        return body(approved).at("/loan/loanId").asText();
    }

    private String disbursedLoan() throws Exception {
        String loanId = approvedLoan();
        mockMvc.perform(post("/api/v1/loans/{loanId}/disburse", loanId))
                .andExpect(status().isOk());
        return loanId;
    }

    private JsonNode body(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
