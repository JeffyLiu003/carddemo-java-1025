package com.carddemo.marketing.interfaces.rest;

import com.carddemo.marketing.application.EvaluateRewardCommand;
import com.carddemo.marketing.application.RewardService;
import com.carddemo.marketing.application.EnrollmentService;
import com.carddemo.marketing.domain.model.AccountPosition;
import com.carddemo.marketing.infrastructure.client.AccountClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 退款与冲正受理 REST 契约：201 正常受理、409 口径未确认、400 参数错误（设计 §5.1）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RewardReversalControllerTest {

    private static final String ACCOUNT_ID = "00000000006";
    private static final String CUSTOMER_ID = "000000001";
    private static final String CARD_NUMBER = "3344556677889900";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EnrollmentService enrollments;

    @Autowired
    private RewardService rewards;

    @MockBean
    private AccountClient accountClient;

    @BeforeEach
    void clearMarketingData() {
        jdbcTemplate.update("delete from reward_reversal");
        jdbcTemplate.update("delete from reward_grant");
        jdbcTemplate.update("delete from enrollment");
        jdbcTemplate.update("delete from campaign");
    }

    /**
     * 通过 REST 建活动（含或省略 reversalPolicy），再报名并评估产生一条 GRANTED 返现。
     */
    private void grantedCampaign(String reversalPolicy) throws Exception {
        String policyField = reversalPolicy == null ? "" : ",\"reversalPolicy\":\"" + reversalPolicy + "\"";
        String body = """
                {"code":"TEST_BONUS","name":"白金卡额外返现","description":"活动说明","startDate":"2026-10-01",
                 "durationMonths":3,"eligibleProductCodes":["PLATINUM"],"minTransactionAmount":0.00,
                 "cashbackRate":0.005,"maxCashbackPerTransaction":300.00,"maxCashbackPerCustomer":3000.00,
                 "budget":50000.00,"enrollmentRequired":true%s}
                """.formatted(policyField);
        MvcResult created = mockMvc.perform(post("/api/v1/campaigns")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        String campaignId = objectMapper.readTree(created.getResponse().getContentAsString())
                .get("campaignId").asText();
        mockMvc.perform(post("/api/v1/campaigns/{campaignId}/activate", campaignId))
                .andExpect(status().isOk());

        when(accountClient.creditPosition(ACCOUNT_ID)).thenReturn(new AccountPosition(ACCOUNT_ID, "PLATINUM",
                new BigDecimal("1000.00"), new BigDecimal("5000.00"), true));
        enrollments.enroll(campaignId, ACCOUNT_ID, CARD_NUMBER, CUSTOMER_ID);
        rewards.evaluate(new EvaluateRewardCommand("TXN-BONUS-1", ACCOUNT_ID, CARD_NUMBER, CUSTOMER_ID,
                new BigDecimal("1000.00"), LocalDateTime.of(2026, 10, 26, 10, 15)));
    }

    private static String reversalBody(String reversalTransactionId, String type, String amount) {
        return """
                {"reversalTransactionId":"%s","originalTransactionId":"TXN-BONUS-1",
                 "reversalType":"%s","reversalAmount":%s}
                """.formatted(reversalTransactionId, type, amount);
    }

    @Test
    void acceptsRefundAndReturnsClawbackDecision() throws Exception {
        grantedCampaign("PROPORTIONAL");

        mockMvc.perform(post("/api/v1/reward-reversals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reversalBody("REV-1", "REFUND", "400.00")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reversals[0].campaignCode").value("TEST_BONUS"))
                .andExpect(jsonPath("$.reversals[0].status").value("PENDING"))
                .andExpect(jsonPath("$.reversals[0].reason").value("CLAWBACK_EXECUTION_PENDING"))
                .andExpect(jsonPath("$.reversals[0].clawbackAmount").value(2.00));
    }

    @Test
    void returnsConflictWhenReversalPolicyIsUnconfirmed() throws Exception {
        grantedCampaign(null);

        mockMvc.perform(post("/api/v1/reward-reversals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reversalBody("REV-2", "REFUND", "1000.00")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REVERSAL_POLICY_UNCONFIRMED"));
    }

    @Test
    void rejectsUnknownReversalType() throws Exception {
        grantedCampaign("FULL");

        mockMvc.perform(post("/api/v1/reward-reversals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reversalBody("REV-3", "REVERSAL", "1000.00")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REVERSAL_TYPE"));
    }

    @Test
    void rejectsMissingReversalTransactionId() throws Exception {
        grantedCampaign("FULL");

        mockMvc.perform(post("/api/v1/reward-reversals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"originalTransactionId":"TXN-BONUS-1","reversalType":"REFUND",
                                 "reversalAmount":1000.00}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }
}
