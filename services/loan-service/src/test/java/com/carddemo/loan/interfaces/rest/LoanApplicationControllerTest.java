package com.carddemo.loan.interfaces.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 贷款申请端到端用例：H2 + Flyway + MockMvc（设计 14 §2.2）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class LoanApplicationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void submitsApplication() throws Exception {
        mockMvc.perform(post("/api/v1/loan-applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(applicationRequest("00000000001", "PERSONAL", "12000.00", 12, "0.1800")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.applicationId").isNotEmpty())
                .andExpect(jsonPath("$.customerId").value("00000000001"))
                .andExpect(jsonPath("$.productCode").value("PERSONAL"))
                .andExpect(jsonPath("$.principal").value(12000.00))
                .andExpect(jsonPath("$.termMonths").value(12))
                .andExpect(jsonPath("$.annualRate").value(0.1800))
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.rejectReason").isEmpty())
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.decidedAt").isEmpty());
    }

    @Test
    void createsApplicationWithBlankCustomerIdReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/loan-applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(applicationRequest(" ", "PERSONAL", "1000.00", 3, "0.1200")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details[0]").value("customerId: must not be blank"));
    }

    @Test
    void createsApplicationWithNonPositivePrincipalReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/loan-applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(applicationRequest("00000000001", "PERSONAL", "0.00", 3, "0.1200")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void createsApplicationWithTermOutOfRangeReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/loan-applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(applicationRequest("00000000001", "PERSONAL", "1000.00", 0, "0.1200")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void createsApplicationWithHundredPercentRateReturns409() throws Exception {
        mockMvc.perform(post("/api/v1/loan-applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(applicationRequest("00000000001", "PERSONAL", "1000.00", 3, "1.0000")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_ANNUAL_RATE"));
    }

    @Test
    void approvesApplicationAndGeneratesThreeInstallmentSchedule() throws Exception {
        String applicationId = submit("00000000002", "PERSONAL", "1000.00", 3, "0.1200");

        MvcResult result = mockMvc.perform(
                        post("/api/v1/loan-applications/{id}/approve", applicationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.application.status").value("APPROVED"))
                .andExpect(jsonPath("$.application.decidedAt").isNotEmpty())
                .andExpect(jsonPath("$.loan.loanId").isNotEmpty())
                .andExpect(jsonPath("$.loan.principal").value(1000.00))
                .andExpect(jsonPath("$.loan.outstandingPrincipal").value(1000.00))
                .andExpect(jsonPath("$.loan.status").value("ACTIVE"))
                .andExpect(jsonPath("$.loan.disbursedAt").isEmpty())
                .andExpect(jsonPath("$.schedule.length()").value(3))
                .andExpect(jsonPath("$.schedule[0].installmentNo").value(1))
                .andExpect(jsonPath("$.schedule[0].principalDue").value(330.02))
                .andExpect(jsonPath("$.schedule[0].interestDue").value(10.00))
                .andExpect(jsonPath("$.schedule[0].totalDue").value(340.02))
                .andExpect(jsonPath("$.schedule[0].status").value("PENDING"))
                .andExpect(jsonPath("$.schedule[2].principalDue").value(336.66))
                .andExpect(jsonPath("$.schedule[2].interestDue").value(3.37))
                .andExpect(jsonPath("$.schedule[2].totalDue").value(340.03))
                .andReturn();

        JsonNode body = body(result);
        LocalDate decidedOn = LocalDateTime.parse(body.at("/application/decidedAt").asText()).toLocalDate();
        assertThat(body.at("/schedule/0/dueDate").asText()).isEqualTo(decidedOn.plusMonths(1).toString());
        assertThat(body.at("/schedule/2/dueDate").asText()).isEqualTo(decidedOn.plusMonths(3).toString());
        BigDecimal principalSum = new BigDecimal(body.at("/schedule/0/principalDue").asText())
                .add(new BigDecimal(body.at("/schedule/1/principalDue").asText()))
                .add(new BigDecimal(body.at("/schedule/2/principalDue").asText()));
        assertThat(principalSum).isEqualByComparingTo("1000.00");
    }

    @Test
    void approvesTwelveInstallmentApplication() throws Exception {
        String applicationId = submit("00000000003", "AUTO", "12000.00", 12, "0.1800");

        mockMvc.perform(post("/api/v1/loan-applications/{id}/approve", applicationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schedule.length()").value(12))
                .andExpect(jsonPath("$.schedule[0].principalDue").value(920.16))
                .andExpect(jsonPath("$.schedule[0].interestDue").value(180.00))
                .andExpect(jsonPath("$.schedule[0].totalDue").value(1100.16))
                .andExpect(jsonPath("$.schedule[11].principalDue").value(1083.93))
                .andExpect(jsonPath("$.schedule[11].interestDue").value(16.26))
                .andExpect(jsonPath("$.schedule[11].totalDue").value(1100.19));
    }

    @Test
    void rejectsApplication() throws Exception {
        String applicationId = submit("00000000004", "PERSONAL", "1000.00", 3, "0.1200");

        mockMvc.perform(post("/api/v1/loan-applications/{id}/reject", applicationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"insufficient income\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejectReason").value("insufficient income"))
                .andExpect(jsonPath("$.decidedAt").isNotEmpty());
    }

    @Test
    void rejectsRejectWithBlankReasonReturns400() throws Exception {
        String applicationId = submit("00000000005", "PERSONAL", "1000.00", 3, "0.1200");

        mockMvc.perform(post("/api/v1/loan-applications/{id}/reject", applicationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void rejectsApprovalOfRejectedApplication() throws Exception {
        String applicationId = submit("00000000006", "PERSONAL", "1000.00", 3, "0.1200");
        mockMvc.perform(post("/api/v1/loan-applications/{id}/reject", applicationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"duplicate application\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/loan-applications/{id}/approve", applicationId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("APPLICATION_NOT_SUBMITTED"));
    }

    @Test
    void rejectsSecondDecisionOnApprovedApplication() throws Exception {
        String applicationId = submit("00000000007", "PERSONAL", "1000.00", 3, "0.1200");
        mockMvc.perform(post("/api/v1/loan-applications/{id}/approve", applicationId))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/loan-applications/{id}/reject", applicationId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"changed mind\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("APPLICATION_NOT_SUBMITTED"));
    }

    @Test
    void returnsApplicationDetailAnd404() throws Exception {
        String applicationId = submit("00000000008", "PERSONAL", "1000.00", 3, "0.1200");

        mockMvc.perform(get("/api/v1/loan-applications/{id}", applicationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applicationId").value(applicationId))
                .andExpect(jsonPath("$.status").value("SUBMITTED"));

        mockMvc.perform(get("/api/v1/loan-applications/{id}", "unknown-application"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LOAN_APPLICATION_NOT_FOUND"));
    }

    @Test
    void approveUnknownApplicationReturns404() throws Exception {
        mockMvc.perform(post("/api/v1/loan-applications/{id}/approve", "unknown-application"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LOAN_APPLICATION_NOT_FOUND"));
    }

    @Test
    void listsApplicationsWithPaginationAndStatusFilter() throws Exception {
        submit("00000000009", "PERSONAL", "1000.00", 3, "0.1200");
        String rejectedId = submit("00000000010", "PERSONAL", "2000.00", 6, "0.0900");
        mockMvc.perform(post("/api/v1/loan-applications/{id}/reject", rejectedId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"policy\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/loan-applications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.items.length()").value(2));

        mockMvc.perform(get("/api/v1/loan-applications").param("page", "1").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.items.length()").value(1));

        mockMvc.perform(get("/api/v1/loan-applications").param("status", "REJECTED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].applicationId").value(rejectedId));
    }

    @Test
    void invalidStatusFilterReturns400() throws Exception {
        mockMvc.perform(get("/api/v1/loan-applications").param("status", "DONE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_APPLICATION_STATUS"));
    }

    private String submit(String customerId, String productCode, String principal, int termMonths,
                          String annualRate) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/loan-applications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(applicationRequest(customerId, productCode, principal, termMonths, annualRate)))
                .andExpect(status().isCreated())
                .andReturn();
        return body(result).get("applicationId").asText();
    }

    private static String applicationRequest(String customerId, String productCode, String principal,
                                             int termMonths, String annualRate) {
        return """
                {"customerId":"%s","productCode":"%s","principal":%s,
                 "termMonths":%d,"annualRate":%s}
                """.formatted(customerId, productCode, principal, termMonths, annualRate);
    }

    private JsonNode body(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }
}
