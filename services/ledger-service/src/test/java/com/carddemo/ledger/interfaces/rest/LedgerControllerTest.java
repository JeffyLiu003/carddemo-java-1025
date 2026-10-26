package com.carddemo.ledger.interfaces.rest;

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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 总账 REST 契约：科目表、日记账、冲正、试算平衡、期间关账。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class LedgerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private static String uniqueSourceId(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }

    private static String journalBody(String sourceId, String postedAt, String debitAmount, String creditAmount) {
        return """
                {"sourceType":"POSTING_BATCH","sourceId":"%s","description":"Card purchase",
                 "postedAt":"%s","currency":"USD",
                 "lines":[{"accountCode":"1100","direction":"DR","amount":%s},
                          {"accountCode":"2100","direction":"CR","amount":%s}]}
                """.formatted(sourceId, postedAt, debitAmount, creditAmount);
    }

    private String createEntry(String sourceId, String postedAt) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/journal-entries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(journalBody(sourceId, postedAt, "25.00", "25.00")))
                .andExpect(status().isCreated())
                .andReturn();
        return entryIdOf(result);
    }

    private String entryIdOf(MvcResult result) throws Exception {
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("entryId").asText();
    }

    @Test
    void listsSeededLedgerAccounts() throws Exception {
        mockMvc.perform(get("/api/v1/ledger-accounts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(9))
                .andExpect(jsonPath("$.items[0].accountCode").value("1000"))
                .andExpect(jsonPath("$.items[0].name").value("Cash"))
                .andExpect(jsonPath("$.items[0].accountType").value("ASSET"))
                .andExpect(jsonPath("$.items[0].active").value(true))
                .andExpect(jsonPath("$.items[8].accountCode").value("9000"));
    }

    @Test
    void returnsLedgerAccountAnd404ForUnknown() throws Exception {
        mockMvc.perform(get("/api/v1/ledger-accounts/1100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountCode").value("1100"))
                .andExpect(jsonPath("$.accountType").value("ASSET"));

        mockMvc.perform(get("/api/v1/ledger-accounts/7777"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LEDGER_ACCOUNT_NOT_FOUND"));
    }

    @Test
    void postsJournalEntryAndReadsItBack() throws Exception {
        String sourceId = uniqueSourceId("post");
        MvcResult created = mockMvc.perform(post("/api/v1/journal-entries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(journalBody(sourceId, "2026-10-26T04:00:00", "25.00", "25.00")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sourceType").value("POSTING_BATCH"))
                .andExpect(jsonPath("$.sourceId").value(sourceId))
                .andExpect(jsonPath("$.status").value("POSTED"))
                .andExpect(jsonPath("$.reversalOf").isEmpty())
                .andExpect(jsonPath("$.postedAt").value("2026-10-26T04:00:00"))
                .andExpect(jsonPath("$.lines.length()").value(2))
                .andExpect(jsonPath("$.lines[0].lineNo").value(1))
                .andExpect(jsonPath("$.lines[0].accountCode").value("1100"))
                .andExpect(jsonPath("$.lines[0].direction").value("DR"))
                .andExpect(jsonPath("$.lines[0].amount").value(25.00))
                .andExpect(jsonPath("$.lines[1].direction").value("CR"))
                .andReturn();

        String entryId = entryIdOf(created);

        mockMvc.perform(get("/api/v1/journal-entries/{entryId}", entryId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entryId").value(entryId))
                .andExpect(jsonPath("$.sourceId").value(sourceId));
    }

    @Test
    void postingIsIdempotentPerSource() throws Exception {
        String sourceId = uniqueSourceId("idempotent");
        String body = journalBody(sourceId, "2026-10-26T04:00:00", "25.00", "25.00");

        String first = entryIdOf(mockMvc.perform(post("/api/v1/journal-entries")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn());
        String second = entryIdOf(mockMvc.perform(post("/api/v1/journal-entries")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn());

        assertThat(second).isEqualTo(first);

        mockMvc.perform(get("/api/v1/journal-entries").param("sourceId", sourceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items.length()").value(1));
    }

    @Test
    void conflictingJournalsForSameSourceReturn409() throws Exception {
        String sourceId = uniqueSourceId("conflict");
        createEntry(sourceId, "2026-10-26T04:00:00");

        mockMvc.perform(post("/api/v1/journal-entries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(journalBody(sourceId, "2026-10-26T04:00:00", "26.00", "26.00")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("JOURNAL_SOURCE_CONFLICT"))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void rejectsUnbalancedJournal() throws Exception {
        mockMvc.perform(post("/api/v1/journal-entries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(journalBody(uniqueSourceId("unbalanced"), "2026-10-26T04:00:00",
                                "25.00", "24.00")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("UNBALANCED_JOURNAL"));
    }

    @Test
    void rejectsUnknownLedgerAccount() throws Exception {
        mockMvc.perform(post("/api/v1/journal-entries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sourceType":"POSTING_BATCH","sourceId":"%s","description":"Bad account",
                                 "postedAt":"2026-10-26T04:00:00","currency":"USD",
                                 "lines":[{"accountCode":"7777","direction":"DR","amount":10.00},
                                          {"accountCode":"2100","direction":"CR","amount":10.00}]}
                                """.formatted(uniqueSourceId("unknown-account"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("LEDGER_ACCOUNT_NOT_FOUND"));
    }

    @Test
    void rejectsPostingIntoClosedPeriod() throws Exception {
        mockMvc.perform(post("/api/v1/journal-entries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(journalBody(uniqueSourceId("closed"), "2026-09-15T10:00:00",
                                "10.00", "10.00")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PERIOD_CLOSED"));
    }

    @Test
    void rejectsRequestWithoutLines() throws Exception {
        mockMvc.perform(post("/api/v1/journal-entries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sourceType":"POSTING_BATCH","sourceId":"no-lines",
                                 "postedAt":"2026-10-26T04:00:00","currency":"USD","lines":[]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details").isArray());
    }

    @Test
    void listsJournalEntriesWithPaging() throws Exception {
        createEntry(uniqueSourceId("page-1"), "2026-10-26T04:00:00");
        createEntry(uniqueSourceId("page-2"), "2026-10-26T05:00:00");

        mockMvc.perform(get("/api/v1/journal-entries").param("page", "0").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.total").value(2));
    }

    @Test
    void trialBalanceTotalsAreEqual() throws Exception {
        createEntry(uniqueSourceId("tb-1"), "2026-11-10T09:00:00");

        mockMvc.perform(get("/api/v1/trial-balance").param("period", "2026-11"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period").value("2026-11"))
                .andExpect(jsonPath("$.totalDebit").value(25.00))
                .andExpect(jsonPath("$.totalCredit").value(25.00))
                .andExpect(jsonPath("$.balanced").value(true))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].accountCode").value("1100"))
                .andExpect(jsonPath("$.items[0].netAmount").value(25.00))
                .andExpect(jsonPath("$.items[1].accountCode").value("2100"))
                .andExpect(jsonPath("$.items[1].netAmount").value(-25.00));
    }

    @Test
    void reversalEndpointSwapsDirectionsAndIsIdempotent() throws Exception {
        String entryId = createEntry(uniqueSourceId("reversal"), "2026-11-10T09:00:00");

        MvcResult reversal = mockMvc.perform(post("/api/v1/journal-entries/{entryId}/reversal", entryId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"postedAt\":\"2026-11-11T09:00:00\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sourceType").value("REVERSAL"))
                .andExpect(jsonPath("$.sourceId").value(entryId))
                .andExpect(jsonPath("$.reversalOf").value(entryId))
                .andExpect(jsonPath("$.lines[0].accountCode").value("1100"))
                .andExpect(jsonPath("$.lines[0].direction").value("CR"))
                .andExpect(jsonPath("$.lines[1].direction").value("DR"))
                .andReturn();
        String reversalId = entryIdOf(reversal);

        mockMvc.perform(get("/api/v1/journal-entries/{entryId}", entryId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REVERSED"));

        mockMvc.perform(post("/api/v1/journal-entries/{entryId}/reversal", entryId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.entryId").value(reversalId));
    }

    @Test
    void listsAccountingPeriods() throws Exception {
        mockMvc.perform(get("/api/v1/accounting-periods"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(3))
                .andExpect(jsonPath("$.items[0].period").value("2026-09"))
                .andExpect(jsonPath("$.items[0].status").value("CLOSED"))
                .andExpect(jsonPath("$.items[0].closedAt").isNotEmpty())
                .andExpect(jsonPath("$.items[1].period").value("2026-10"))
                .andExpect(jsonPath("$.items[1].status").value("OPEN"))
                .andExpect(jsonPath("$.items[2].period").value("2026-11"))
                .andExpect(jsonPath("$.items[2].status").value("OPEN"));
    }

    @Test
    void closeAccountingPeriod() throws Exception {
        mockMvc.perform(post("/api/v1/accounting-periods/2026-11/close"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period").value("2026-11"))
                .andExpect(jsonPath("$.status").value("CLOSED"))
                .andExpect(jsonPath("$.closedAt").isNotEmpty());

        mockMvc.perform(post("/api/v1/accounting-periods/2026-11/close"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PERIOD_ALREADY_CLOSED"));

        mockMvc.perform(post("/api/v1/accounting-periods/2026-12/close"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PERIOD_NOT_FOUND"));

        mockMvc.perform(post("/api/v1/accounting-periods/2026-13/close"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PERIOD"));
    }

    @Test
    void rejectsInvalidTrialBalancePeriod() throws Exception {
        mockMvc.perform(get("/api/v1/trial-balance").param("period", "2026-13"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_PERIOD"));

        mockMvc.perform(get("/api/v1/trial-balance"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }
}
