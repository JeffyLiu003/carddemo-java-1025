package com.carddemo.transaction.interfaces.rest;

import com.carddemo.transaction.domain.exception.RemoteServiceException;
import com.carddemo.transaction.domain.model.AccountSnapshot;
import com.carddemo.transaction.infrastructure.client.AccountClient;
import com.carddemo.transaction.infrastructure.client.LedgerClient;
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
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 过账引擎端到端用例：H2 + Flyway 种子 + MockMvc；account-service / ledger-service 用 @MockBean 替换。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PostingBatchControllerTest {

    private static final String ACCOUNT_ID = "00000000006";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private AccountClient accountClient;

    @MockBean
    private LedgerClient ledgerClient;

    @BeforeEach
    void stubDownstream() {
        when(accountClient.findAccount(ACCOUNT_ID)).thenReturn(Optional.of(account("Y")));
        when(accountClient.applyPosting(eq(ACCOUNT_ID), any())).thenReturn(account("Y"));
        when(ledgerClient.recordJournalEntry(any())).thenReturn("JE-0001");
    }

    @Test
    void listsSevenSeededPostingTypes() throws Exception {
        mockMvc.perform(get("/api/v1/posting-types"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(7))
                .andExpect(jsonPath("$.items[0].typeCode").value("ADJUSTMENT"))
                .andExpect(jsonPath("$.items[0].name").value("调账"))
                .andExpect(jsonPath("$.items[0].accountEffect").value("INCREASE"))
                .andExpect(jsonPath("$.items[0].active").value(true))
                .andExpect(jsonPath("$.items[1].typeCode").value("CARD_PURCHASE"))
                .andExpect(jsonPath("$.items[1].name").value("信用卡消费"))
                .andExpect(jsonPath("$.items[5].typeCode").value("PAYMENT"))
                .andExpect(jsonPath("$.items[5].accountEffect").value("DECREASE"));
    }

    @Test
    void returnsPostingTypeWithRules() throws Exception {
        mockMvc.perform(get("/api/v1/posting-types/CARD_PURCHASE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.typeCode").value("CARD_PURCHASE"))
                .andExpect(jsonPath("$.accountEffect").value("INCREASE"))
                .andExpect(jsonPath("$.rules.length()").value(2))
                .andExpect(jsonPath("$.rules[0].legNo").value(1))
                .andExpect(jsonPath("$.rules[0].accountRole").value("CARD_RECEIVABLE"))
                .andExpect(jsonPath("$.rules[0].direction").value("DR"))
                .andExpect(jsonPath("$.rules[0].ledgerAccountCode").value("1100"))
                .andExpect(jsonPath("$.rules[1].direction").value("CR"))
                .andExpect(jsonPath("$.rules[1].ledgerAccountCode").value("2100"));
    }

    @Test
    void returns404ForUnknownPostingType() throws Exception {
        mockMvc.perform(get("/api/v1/posting-types/NOPE"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("POSTING_TYPE_NOT_FOUND"));
    }

    @Test
    void createsPostingBatchWithBalancedLegs() throws Exception {
        mockMvc.perform(post("/api/v1/posting-batches")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request("post-1001", "CARD_PURCHASE", "25.00")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.batchId").isNotEmpty())
                .andExpect(jsonPath("$.idempotencyKey").value("post-1001"))
                .andExpect(jsonPath("$.typeCode").value("CARD_PURCHASE"))
                .andExpect(jsonPath("$.typeName").value("信用卡消费"))
                .andExpect(jsonPath("$.accountId").value(ACCOUNT_ID))
                .andExpect(jsonPath("$.amount").value(25.00))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.description").value("Purchase at Corner Grocery"))
                .andExpect(jsonPath("$.status").value("POSTED"))
                .andExpect(jsonPath("$.reversalOf").isEmpty())
                .andExpect(jsonPath("$.ledgerEntryId").value("JE-0001"))
                .andExpect(jsonPath("$.legs.length()").value(2))
                .andExpect(jsonPath("$.legs[0].legNo").value(1))
                .andExpect(jsonPath("$.legs[0].accountRole").value("CARD_RECEIVABLE"))
                .andExpect(jsonPath("$.legs[0].direction").value("DR"))
                .andExpect(jsonPath("$.legs[0].ledgerAccountCode").value("1100"))
                .andExpect(jsonPath("$.legs[0].amount").value(25.00))
                .andExpect(jsonPath("$.legs[1].direction").value("CR"))
                .andExpect(jsonPath("$.legs[1].ledgerAccountCode").value("2100"));
    }

    @Test
    void repeatedIdempotencyKeyReturnsOriginalBatch() throws Exception {
        String first = createBatch("post-1002", "CARD_PURCHASE", "25.00");
        String second = createBatch("post-1002", "CARD_PURCHASE", "25.00");

        assertThat(second).isEqualTo(first);
    }

    @Test
    void idempotencyConflictReturns409() throws Exception {
        createBatch("post-1003", "CARD_PURCHASE", "25.00");

        mockMvc.perform(post("/api/v1/posting-batches")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request("post-1003", "CARD_PURCHASE", "30.00")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));
    }

    @Test
    void invalidAmountReturns409() throws Exception {
        mockMvc.perform(post("/api/v1/posting-batches")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request("post-1004", "CARD_PURCHASE", "0.00")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_POSTING_AMOUNT"));
    }

    @Test
    void inactivePostingTypeReturns409() throws Exception {
        jdbcTemplate.update("update posting_type set active = false where type_code = 'FEE'");

        mockMvc.perform(post("/api/v1/posting-batches")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request("post-1005", "FEE", "15.00")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("POSTING_TYPE_INACTIVE"));
    }

    @Test
    void unknownAccountReturns404() throws Exception {
        when(accountClient.findAccount("99999999999")).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/v1/posting-batches")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"idempotencyKey":"post-1006","typeCode":"CARD_PURCHASE",
                                 "accountId":"99999999999","amount":25.00,"currency":"USD",
                                 "description":"Purchase at Corner Grocery"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"));
    }

    @Test
    void inactiveAccountReturns409() throws Exception {
        when(accountClient.findAccount(ACCOUNT_ID)).thenReturn(Optional.of(account("N")));

        mockMvc.perform(post("/api/v1/posting-batches")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request("post-1007", "CARD_PURCHASE", "25.00")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACCOUNT_INACTIVE"));
    }

    @Test
    void accountServiceUnavailableReturns503() throws Exception {
        when(accountClient.applyPosting(any(), any()))
                .thenThrow(new RemoteServiceException("account-service is unavailable"));

        mockMvc.perform(post("/api/v1/posting-batches")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request("post-1008", "CARD_PURCHASE", "25.00")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("DEPENDENCY_UNAVAILABLE"));
    }

    @Test
    void returnsBatchDetailAnd404ForUnknownBatch() throws Exception {
        String batchId = createBatch("post-1009", "PAYMENT", "40.00");

        mockMvc.perform(get("/api/v1/posting-batches/{batchId}", batchId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.batchId").value(batchId))
                .andExpect(jsonPath("$.typeName").value("还款"))
                .andExpect(jsonPath("$.legs[0].direction").value("DR"))
                .andExpect(jsonPath("$.legs[0].ledgerAccountCode").value("1000"))
                .andExpect(jsonPath("$.legs[1].ledgerAccountCode").value("1100"));

        mockMvc.perform(get("/api/v1/posting-batches/{batchId}", "unknown-batch"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("POSTING_BATCH_NOT_FOUND"));
    }

    @Test
    void listsBatchesWithPaginationAndFilters() throws Exception {
        createBatch("post-1010", "CARD_PURCHASE", "25.00");
        createBatch("post-1011", "PAYMENT", "10.00");

        mockMvc.perform(get("/api/v1/posting-batches"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.items.length()").value(2));

        mockMvc.perform(get("/api/v1/posting-batches").param("page", "0").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.items.length()").value(1));

        mockMvc.perform(get("/api/v1/posting-batches").param("accountId", ACCOUNT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2));

        mockMvc.perform(get("/api/v1/posting-batches").param("idempotencyKey", "post-1011"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].idempotencyKey").value("post-1011"));

        mockMvc.perform(get("/api/v1/posting-batches").param("accountId", "00000000001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void reversalCreatesSwappedBatchAndMarksOriginalReversed() throws Exception {
        String batchId = createBatch("post-1012", "CARD_PURCHASE", "25.00");

        MvcResult result = mockMvc.perform(post("/api/v1/posting-batches/{batchId}/reversal", batchId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reversalOf").value(batchId))
                .andExpect(jsonPath("$.idempotencyKey").value("reversal:" + batchId))
                .andExpect(jsonPath("$.status").value("POSTED"))
                .andExpect(jsonPath("$.legs[0].direction").value("CR"))
                .andExpect(jsonPath("$.legs[0].ledgerAccountCode").value("1100"))
                .andExpect(jsonPath("$.legs[1].direction").value("DR"))
                .andExpect(jsonPath("$.legs[1].ledgerAccountCode").value("2100"))
                .andReturn();

        String reversalId = body(result).get("batchId").asText();
        assertThat(reversalId).isNotEqualTo(batchId);

        mockMvc.perform(get("/api/v1/posting-batches/{batchId}", batchId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REVERSED"))
                .andExpect(jsonPath("$.reversedAt").isNotEmpty());
    }

    @Test
    void repeatedReversalReturnsExistingReversalBatch() throws Exception {
        String batchId = createBatch("post-1013", "CARD_PURCHASE", "25.00");

        String first = body(mockMvc.perform(post("/api/v1/posting-batches/{batchId}/reversal", batchId))
                .andExpect(status().isOk()).andReturn()).get("batchId").asText();
        String second = body(mockMvc.perform(post("/api/v1/posting-batches/{batchId}/reversal", batchId))
                .andExpect(status().isOk()).andReturn()).get("batchId").asText();

        assertThat(second).isEqualTo(first);
    }

    @Test
    void reversalOfReversalReturns409() throws Exception {
        String batchId = createBatch("post-1014", "CARD_PURCHASE", "25.00");
        String reversalId = body(mockMvc.perform(post("/api/v1/posting-batches/{batchId}/reversal", batchId))
                .andExpect(status().isOk()).andReturn()).get("batchId").asText();

        mockMvc.perform(post("/api/v1/posting-batches/{batchId}/reversal", reversalId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REVERSAL_NOT_ALLOWED"));
    }

    @Test
    void returns400WhenIdempotencyKeyIsMissing() throws Exception {
        mockMvc.perform(post("/api/v1/posting-batches")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"typeCode":"CARD_PURCHASE","accountId":"00000000006","amount":25.00}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    private String createBatch(String idempotencyKey, String typeCode, String amount) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/posting-batches")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(idempotencyKey, typeCode, amount)))
                .andExpect(status().isCreated())
                .andReturn();
        return body(result).get("batchId").asText();
    }

    private static String request(String idempotencyKey, String typeCode, String amount) {
        return """
                {"idempotencyKey":"%s","typeCode":"%s","accountId":"%s","amount":%s,"currency":"USD",
                 "description":"Purchase at Corner Grocery"}
                """.formatted(idempotencyKey, typeCode, ACCOUNT_ID, amount);
    }

    private JsonNode body(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private static AccountSnapshot account(String activeStatus) {
        return new AccountSnapshot(ACCOUNT_ID, activeStatus, new BigDecimal("1940.00"),
                new BigDecimal("5000.00"), new BigDecimal("1020.00"), new BigDecimal("0.00"),
                new BigDecimal("0.00"), LocalDate.of(2028, 5, 20), "A000000000");
    }
}
