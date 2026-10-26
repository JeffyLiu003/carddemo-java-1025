package com.carddemo.fx.interfaces.rest;

import com.carddemo.fx.support.FxTestClockConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 外汇 REST 契约：汇率 upsert/列表、报价计算、执行换汇、重复消费与过期。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@Import(FxTestClockConfig.class)
class FxControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void resetClock() {
        FxTestClockConfig.CLOCK.moveTo(FxTestClockConfig.BASE_INSTANT);
    }

    private JsonNode body(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String createQuote(String fromCurrency, String toCurrency, String amount) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/fx/quotes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fromCurrency":"%s","toCurrency":"%s","amount":%s}
                                """.formatted(fromCurrency, toCurrency, amount)))
                .andExpect(status().isCreated())
                .andReturn();
        return body(result).get("quoteId").asText();
    }

    @Test
    void listsSeededRates() throws Exception {
        mockMvc.perform(get("/api/v1/fx/rates"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.items.length()").value(3))
                .andExpect(jsonPath("$.items[0].baseCurrency").value("EUR"))
                .andExpect(jsonPath("$.items[0].quoteCurrency").value("CNY"))
                .andExpect(jsonPath("$.items[0].rate").value(7.72))
                .andExpect(jsonPath("$.items[0].active").value(true))
                .andExpect(jsonPath("$.items[1].baseCurrency").value("USD"))
                .andExpect(jsonPath("$.items[1].quoteCurrency").value("CNY"))
                .andExpect(jsonPath("$.items[1].rate").value(7.10))
                .andExpect(jsonPath("$.items[2].baseCurrency").value("USD"))
                .andExpect(jsonPath("$.items[2].quoteCurrency").value("EUR"))
                .andExpect(jsonPath("$.items[2].rate").value(0.92));
    }

    @Test
    void upsertsRateAndKeepsSinglePair() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/v1/fx/rates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"baseCurrency":"gbp","quoteCurrency":"usd","rate":1.27}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.baseCurrency").value("GBP"))
                .andExpect(jsonPath("$.quoteCurrency").value("USD"))
                .andExpect(jsonPath("$.rate").value(1.27))
                .andExpect(jsonPath("$.active").value(true))
                .andReturn();
        String pairId = body(created).get("pairId").asText();

        mockMvc.perform(post("/api/v1/fx/rates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"baseCurrency":"GBP","quoteCurrency":"USD","rate":1.280000,
                                 "effectiveAt":"2026-10-27T00:00:00"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pairId").value(pairId))
                .andExpect(jsonPath("$.rate").value(1.28))
                .andExpect(jsonPath("$.effectiveAt").value("2026-10-27T00:00:00"));

        mockMvc.perform(get("/api/v1/fx/rates").param("page", "0").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(4));
    }

    @Test
    void quotesWithFixedClockAndFiveMinuteExpiry() throws Exception {
        mockMvc.perform(post("/api/v1/fx/quotes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fromCurrency":"USD","toCurrency":"CNY","amount":100.00}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fromCurrency").value("USD"))
                .andExpect(jsonPath("$.toCurrency").value("CNY"))
                .andExpect(jsonPath("$.sourceAmount").value(100.00))
                .andExpect(jsonPath("$.rate").value(7.10))
                .andExpect(jsonPath("$.convertedAmount").value(710.00))
                .andExpect(jsonPath("$.createdAt").value("2026-10-26T04:00:00"))
                .andExpect(jsonPath("$.expiresAt").value("2026-10-26T04:05:00"))
                .andExpect(jsonPath("$.consumed").value(false));
    }

    @Test
    void executesQuoteOnceAndListsConversion() throws Exception {
        String quoteId = createQuote("USD", "EUR", "33.33");

        mockMvc.perform(post("/api/v1/fx/quotes/{quoteId}/execute", quoteId))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.quoteId").value(quoteId))
                .andExpect(jsonPath("$.fromCurrency").value("USD"))
                .andExpect(jsonPath("$.toCurrency").value("EUR"))
                .andExpect(jsonPath("$.sourceAmount").value(33.33))
                .andExpect(jsonPath("$.rate").value(0.92))
                .andExpect(jsonPath("$.convertedAmount").value(30.66))
                .andExpect(jsonPath("$.createdAt").value("2026-10-26T04:00:00"));

        mockMvc.perform(get("/api/v1/fx/conversions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].quoteId").value(quoteId))
                .andExpect(jsonPath("$.items[0].convertedAmount").value(30.66));

        mockMvc.perform(post("/api/v1/fx/quotes/{quoteId}/execute", quoteId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("QUOTE_ALREADY_CONSUMED"))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void expiredQuoteCannotBeExecuted() throws Exception {
        String quoteId = createQuote("USD", "CNY", "100.00");

        FxTestClockConfig.CLOCK.advance(Duration.ofMinutes(6));

        mockMvc.perform(post("/api/v1/fx/quotes/{quoteId}/execute", quoteId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("QUOTE_EXPIRED"));
    }

    @Test
    void unknownQuoteAndRateReturn404() throws Exception {
        mockMvc.perform(post("/api/v1/fx/quotes/{quoteId}/execute", "missing-quote"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("QUOTE_NOT_FOUND"));

        mockMvc.perform(post("/api/v1/fx/quotes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fromCurrency":"GBP","toCurrency":"JPY","amount":10.00}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RATE_NOT_FOUND"));
    }

    @Test
    void rejectsSameCurrencyQuote() throws Exception {
        mockMvc.perform(post("/api/v1/fx/quotes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fromCurrency":"USD","toCurrency":"USD","amount":10.00}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SAME_CURRENCY"));
    }

    @Test
    void rejectsInvalidRequestsWith400() throws Exception {
        mockMvc.perform(post("/api/v1/fx/quotes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fromCurrency":"USD","toCurrency":"CNY","amount":0.00}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details").isArray());

        mockMvc.perform(post("/api/v1/fx/quotes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fromCurrency":"US","toCurrency":"CNY","amount":10.00}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        mockMvc.perform(post("/api/v1/fx/rates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"baseCurrency":"USD","quoteCurrency":"CNY"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        mockMvc.perform(post("/api/v1/fx/rates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"baseCurrency":"USD","quoteCurrency":"CNY","rate":-1.0}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void conversionsArePagedNewestFirst() throws Exception {
        String first = createQuote("USD", "CNY", "10.00");
        FxTestClockConfig.CLOCK.advance(Duration.ofSeconds(30));
        String second = createQuote("USD", "CNY", "20.00");

        mockMvc.perform(post("/api/v1/fx/quotes/{quoteId}/execute", first))
                .andExpect(status().isCreated());
        FxTestClockConfig.CLOCK.advance(Duration.ofSeconds(1));
        mockMvc.perform(post("/api/v1/fx/quotes/{quoteId}/execute", second))
                .andExpect(status().isCreated());

        MvcResult result = mockMvc.perform(get("/api/v1/fx/conversions")
                        .param("page", "0").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andReturn();

        assertThat(body(result).get("items").get(0).get("quoteId").asText()).isEqualTo(second);
    }
}
