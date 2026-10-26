package com.carddemo.marketing.interfaces.rest;

import com.carddemo.marketing.domain.exception.RemoteServiceException;
import com.carddemo.marketing.domain.model.AccountPosition;
import com.carddemo.marketing.infrastructure.client.AccountClient;
import com.fasterxml.jackson.databind.JsonNode;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 营销 REST 契约：创建 201、列表/详情、发布暂停结束 200/409、报名 201/200、资格、评估 201、重试、看板。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class MarketingControllerTest {

    private static final String ACCOUNT_ID = "00000000006";
    private static final String CUSTOMER_ID = "000000001";
    private static final String CARD_NUMBER = "3344556677889900";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private AccountClient accountClient;

    @BeforeEach
    void clearMarketingData() {
        jdbcTemplate.update("delete from reward_grant");
        jdbcTemplate.update("delete from enrollment");
        jdbcTemplate.update("delete from campaign");
    }

    private static String createBody(String code, String productCode, int durationMonths, String rate) {
        return """
                {"code":"%s","name":"活动 %s","description":"活动说明","startDate":"2026-10-01",
                 "durationMonths":%d,"eligibleProductCodes":["%s"],
                 "minTransactionAmount":50.00,"cashbackRate":%s,
                 "maxCashbackPerTransaction":30.00,"maxCashbackPerCustomer":300.00,
                 "budget":50000.00,"enrollmentRequired":true}
                """.formatted(code, code, durationMonths, productCode, rate);
    }

    private String createCampaign(String code, String productCode) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/campaigns")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(code, productCode, 3, "0.05")))
                .andExpect(status().isCreated())
                .andReturn();
        return valueOf(result, "campaignId");
    }

    private String valueOf(MvcResult result, String field) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).get(field).asText();
    }

    private void activate(String campaignId) throws Exception {
        mockMvc.perform(post("/api/v1/campaigns/{campaignId}/activate", campaignId))
                .andExpect(status().isOk());
    }

    private void enroll(String campaignId) throws Exception {
        mockMvc.perform(post("/api/v1/campaigns/{campaignId}/enrollments", campaignId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"accountId":"%s","customerId":"%s","cardNumber":"%s"}
                                """.formatted(ACCOUNT_ID, CUSTOMER_ID, CARD_NUMBER)))
                .andExpect(status().isCreated());
    }

    private void stubAccount(String productCode) {
        when(accountClient.creditPosition(ACCOUNT_ID)).thenReturn(new AccountPosition(ACCOUNT_ID, productCode,
                new BigDecimal("1000.00"), new BigDecimal("5000.00"), true));
    }

    @Test
    void createsCampaignAndReturnsDetailOr404() throws Exception {
        mockMvc.perform(post("/api/v1/campaigns")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("TEST_PLATINUM", "PLATINUM", 3, "0.05")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.campaignId").isNotEmpty())
                .andExpect(jsonPath("$.code").value("TEST_PLATINUM"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.startDate").value("2026-10-01"))
                .andExpect(jsonPath("$.durationMonths").value(3))
                .andExpect(jsonPath("$.endDate").value("2026-12-31"))
                .andExpect(jsonPath("$.eligibleProductCodes[0]").value("PLATINUM"))
                .andExpect(jsonPath("$.minTransactionAmount").value(50.00))
                .andExpect(jsonPath("$.cashbackRate").value(0.05))
                .andExpect(jsonPath("$.budget").value(50000.00))
                .andExpect(jsonPath("$.consumedBudget").value(0.00))
                .andExpect(jsonPath("$.budgetRemaining").value(50000.00))
                .andExpect(jsonPath("$.enrollmentRequired").value(true));

        String campaignId = createCampaign("TEST_PLATINUM_2", "PLATINUM");
        mockMvc.perform(get("/api/v1/campaigns/{campaignId}", campaignId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("TEST_PLATINUM_2"));

        mockMvc.perform(get("/api/v1/campaigns/{campaignId}", "missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CAMPAIGN_NOT_FOUND"));
    }

    @Test
    void createRejectsInvalidPayloadAndDuplicateCode() throws Exception {
        mockMvc.perform(post("/api/v1/campaigns")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("TEST_BAD_DURATION", "PLATINUM", 30, "0.05")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        mockMvc.perform(post("/api/v1/campaigns")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("TEST_BAD_RATE", "PLATINUM", 3, "1.5")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CAMPAIGN"));

        mockMvc.perform(post("/api/v1/campaigns")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"TEST_NO_NAME","startDate":"2026-10-01","durationMonths":3,
                                 "eligibleProductCodes":["PLATINUM"],"minTransactionAmount":50.00,
                                 "cashbackRate":0.05,"maxCashbackPerTransaction":30.00,
                                 "maxCashbackPerCustomer":300.00,"budget":50000.00}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        createCampaign("TEST_DUPLICATE", "PLATINUM");
        mockMvc.perform(post("/api/v1/campaigns")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("TEST_DUPLICATE", "PLATINUM", 3, "0.05")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CAMPAIGN_CODE_ALREADY_EXISTS"));
    }

    @Test
    void lifecycleTransitionsReturn200AndIllegalTransition409() throws Exception {
        String campaignId = createCampaign("TEST_LIFECYCLE", "PLATINUM");

        mockMvc.perform(post("/api/v1/campaigns/{campaignId}/activate", campaignId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        mockMvc.perform(post("/api/v1/campaigns/{campaignId}/pause", campaignId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAUSED"));
        mockMvc.perform(post("/api/v1/campaigns/{campaignId}/end", campaignId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ENDED"));
        mockMvc.perform(post("/api/v1/campaigns/{campaignId}/pause", campaignId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CAMPAIGN_INVALID_TRANSITION"));
        mockMvc.perform(post("/api/v1/campaigns/{campaignId}/activate", "missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CAMPAIGN_NOT_FOUND"));
    }

    @Test
    void enrollmentIsCreatedThenIdempotent() throws Exception {
        String campaignId = createCampaign("TEST_ENROLL", "PLATINUM");
        activate(campaignId);
        stubAccount("PLATINUM");

        MvcResult first = mockMvc.perform(post("/api/v1/campaigns/{campaignId}/enrollments", campaignId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"accountId":"%s","customerId":"%s","cardNumber":"%s"}
                                """.formatted(ACCOUNT_ID, CUSTOMER_ID, CARD_NUMBER)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.enrollmentId").isNotEmpty())
                .andExpect(jsonPath("$.accountId").value(ACCOUNT_ID))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andReturn();

        mockMvc.perform(post("/api/v1/campaigns/{campaignId}/enrollments", campaignId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"accountId":"%s","customerId":"%s","cardNumber":"%s"}
                                """.formatted(ACCOUNT_ID, CUSTOMER_ID, CARD_NUMBER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enrollmentId").value(valueOf(first, "enrollmentId")));

        mockMvc.perform(get("/api/v1/campaigns/{campaignId}/enrollments", campaignId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items.length()").value(1));
    }

    @Test
    void enrollmentRejectedForProductMismatch() throws Exception {
        String campaignId = createCampaign("TEST_GOLD", "GOLD");
        activate(campaignId);
        stubAccount("PLATINUM");

        mockMvc.perform(post("/api/v1/campaigns/{campaignId}/enrollments", campaignId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"accountId":"%s","customerId":"%s","cardNumber":"%s"}
                                """.formatted(ACCOUNT_ID, CUSTOMER_ID, CARD_NUMBER)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRODUCT_NOT_ELIGIBLE"));
    }

    @Test
    void eligibilitiesExposeCampaignFlags() throws Exception {
        String platinum = createCampaign("TEST_PLATINUM", "PLATINUM");
        activate(platinum);
        String gold = createCampaign("TEST_GOLD", "GOLD");
        activate(gold);
        createCampaign("TEST_DRAFT", "PLATINUM");
        stubAccount("PLATINUM");
        enroll(platinum);

        MvcResult result = mockMvc.perform(get("/api/v1/eligibilities")
                        .param("accountId", ACCOUNT_ID)
                        .param("cardNumber", CARD_NUMBER)
                        .param("customerId", CUSTOMER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andReturn();

        JsonNode items = objectMapper.readTree(result.getResponse().getContentAsString()).get("items");
        JsonNode platinumNode = itemOf(items, "TEST_PLATINUM");
        JsonNode goldNode = itemOf(items, "TEST_GOLD");
        assertThat(platinumNode.get("eligible").asBoolean()).isTrue();
        assertThat(platinumNode.get("enrolled").asBoolean()).isTrue();
        assertThat(platinumNode.get("enrollmentRequired").asBoolean()).isTrue();
        assertThat(platinumNode.get("rewardSummary").asText()).contains("5%");
        assertThat(platinumNode.get("endDate").asText()).isEqualTo("2026-12-31");
        assertThat(goldNode.get("eligible").asBoolean()).isFalse();
        assertThat(goldNode.get("enrolled").asBoolean()).isFalse();
    }

    private JsonNode itemOf(JsonNode items, String code) {
        for (JsonNode item : items) {
            if (code.equals(item.get("code").asText())) {
                return item;
            }
        }
        throw new AssertionError("Campaign " + code + " missing from eligibilities");
    }

    @Test
    void evaluationRetryRewardsPerformanceAndSummary() throws Exception {
        String campaignId = createCampaign("TEST_PLATINUM", "PLATINUM");
        activate(campaignId);
        stubAccount("PLATINUM");
        enroll(campaignId);
        doThrow(new RemoteServiceException("account-service is unavailable"))
                .doNothing()
                .when(accountClient).credit(eq(ACCOUNT_ID), any(), any(), any());

        MvcResult evaluation = mockMvc.perform(post("/api/v1/reward-evaluations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"transactionId":"TXN-200","accountId":"%s","cardNumber":"%s",
                                 "customerId":"%s","amount":1000.00,"occurredAt":"2026-10-26T10:15:00"}
                                """.formatted(ACCOUNT_ID, CARD_NUMBER, CUSTOMER_ID)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.grants.length()").value(1))
                .andExpect(jsonPath("$.grants[0].campaignCode").value("TEST_PLATINUM"))
                .andExpect(jsonPath("$.grants[0].cashbackAmount").value(30.00))
                .andExpect(jsonPath("$.grants[0].status").value("PENDING"))
                .andExpect(jsonPath("$.grants[0].failureReason").isNotEmpty())
                .andReturn();

        mockMvc.perform(get("/api/v1/rewards").param("accountId", ACCOUNT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].status").value("PENDING"));

        mockMvc.perform(post("/api/v1/rewards/{rewardId}/retry", rewardIdOf(evaluation)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rewardId").value(rewardIdOf(evaluation)))
                .andExpect(jsonPath("$.status").value("GRANTED"))
                .andExpect(jsonPath("$.grantedAt").isNotEmpty())
                .andExpect(jsonPath("$.failureReason").isEmpty());

        mockMvc.perform(get("/api/v1/campaigns/{campaignId}/performance", campaignId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enrolledCount").value(1))
                .andExpect(jsonPath("$.rewardCount").value(1))
                .andExpect(jsonPath("$.grantedCount").value(1))
                .andExpect(jsonPath("$.pendingCount").value(0))
                .andExpect(jsonPath("$.cashbackTotal").value(30.00))
                .andExpect(jsonPath("$.budgetUsageRate").value(0.0006))
                .andExpect(jsonPath("$.conversionRate").value(1.0000));

        mockMvc.perform(get("/api/v1/marketing/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeCampaigns").value(1))
                .andExpect(jsonPath("$.grantedRewards").value(1))
                .andExpect(jsonPath("$.cashbackTotal").value(30.00))
                .andExpect(jsonPath("$.pendingRewards").value(0));
    }

    private String rewardIdOf(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("grants").get(0).get("rewardId").asText();
    }
}
