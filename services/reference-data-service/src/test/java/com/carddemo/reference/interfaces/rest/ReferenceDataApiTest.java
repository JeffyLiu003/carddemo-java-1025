package com.carddemo.reference.interfaces.rest;

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

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReferenceDataApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private JsonNode body(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private JsonNode create(String domain, String code, String label, Integer sortOrder,
                           Boolean active) throws Exception {
        var node = objectMapper.createObjectNode()
                .put("domain", domain)
                .put("code", code)
                .put("label", label);
        if (sortOrder != null) {
            node.put("sortOrder", sortOrder);
        }
        if (active != null) {
            node.put("active", active);
        }
        MvcResult created = mockMvc.perform(post("/api/v1/reference")
                        .contentType(MediaType.APPLICATION_JSON).content(node.toString()))
                .andExpect(status().isCreated())
                .andReturn();
        return body(created);
    }

    private List<String> codes(JsonNode listBody) {
        List<String> codes = new ArrayList<>();
        for (JsonNode item : listBody.get("items")) {
            codes.add(item.get("code").asText());
        }
        return codes;
    }

    @Test
    void listsSeededReferenceData() throws Exception {
        MvcResult currencies = mockMvc.perform(get("/api/v1/reference/CURRENCY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(3))
                .andReturn();
        assertThat(codes(body(currencies))).containsExactly("USD", "CNY", "EUR");
        assertThat(body(currencies).get("items").get(0).get("label").asText()).isEqualTo("US Dollar");

        MvcResult countries = mockMvc.perform(get("/api/v1/reference/COUNTRY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(3))
                .andReturn();
        assertThat(codes(body(countries))).containsExactly("US", "CN", "SG");

        MvcResult holidays = mockMvc.perform(get("/api/v1/reference/HOLIDAY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andReturn();
        assertThat(codes(body(holidays))).containsExactly("2026-10-01", "2026-12-25");

        MvcResult categories = mockMvc.perform(get("/api/v1/reference/PRODUCT_CATEGORY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(3))
                .andReturn();
        assertThat(codes(body(categories))).containsExactly("CLASSIC", "GOLD", "PLATINUM");
        assertThat(body(categories).get("items").get(2).get("label").asText()).isEqualTo("Platinum Card");
    }

    @Test
    void readsDetailIgnoringInputCase() throws Exception {
        mockMvc.perform(get("/api/v1/reference/currency/USD"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.domain").value("CURRENCY"))
                .andExpect(jsonPath("$.code").value("USD"))
                .andExpect(jsonPath("$.label").value("US Dollar"))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void filtersByActiveFlag() throws Exception {
        create("SEGMENT", "VIP", "高价值客户", 10, true);
        create("SEGMENT", "MASS", "大众客户", 20, false);

        mockMvc.perform(get("/api/v1/reference/SEGMENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2));
        mockMvc.perform(get("/api/v1/reference/SEGMENT").param("active", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].code").value("VIP"));
        mockMvc.perform(get("/api/v1/reference/SEGMENT").param("active", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].code").value("MASS"));
    }

    @Test
    void createsItemAndRejectsDuplicate() throws Exception {
        JsonNode created = create("channel", "mobile", "手机银行", 5, null);
        assertThat(created.get("itemId").asText()).isNotBlank();
        assertThat(created.get("domain").asText()).isEqualTo("CHANNEL");
        assertThat(created.get("code").asText()).isEqualTo("MOBILE");
        assertThat(created.get("sortOrder").asInt()).isEqualTo(5);
        assertThat(created.get("active").asBoolean()).isTrue();

        mockMvc.perform(get("/api/v1/reference/CHANNEL/MOBILE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.label").value("手机银行"));

        mockMvc.perform(post("/api/v1/reference")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"domain\":\"Channel\",\"code\":\"MOBILE\",\"label\":\"手机银行（重复）\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REFERENCE_ITEM_EXISTS"));
    }

    @Test
    void updatesLabelSortOrderAndActive() throws Exception {
        JsonNode created = create("PARTNER_TYPE", "ISSUER", "发卡机构", 10, true);
        String itemId = created.get("itemId").asText();

        mockMvc.perform(put("/api/v1/reference/" + itemId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"label\":\"发卡行\",\"sortOrder\":15,\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itemId").value(itemId))
                .andExpect(jsonPath("$.domain").value("PARTNER_TYPE"))
                .andExpect(jsonPath("$.code").value("ISSUER"))
                .andExpect(jsonPath("$.label").value("发卡行"))
                .andExpect(jsonPath("$.sortOrder").value(15))
                .andExpect(jsonPath("$.active").value(false));

        mockMvc.perform(get("/api/v1/reference/PARTNER_TYPE/ISSUER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.label").value("发卡行"))
                .andExpect(jsonPath("$.active").value(false));
        mockMvc.perform(get("/api/v1/reference/PARTNER_TYPE").param("active", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void deletesItem() throws Exception {
        JsonNode created = create("CARD_MEDIUM", "VIRTUAL", "虚拟卡", 10, true);
        String itemId = created.get("itemId").asText();

        mockMvc.perform(delete("/api/v1/reference/" + itemId))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/reference/CARD_MEDIUM/VIRTUAL"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("REFERENCE_ITEM_NOT_FOUND"));
        mockMvc.perform(delete("/api/v1/reference/" + itemId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("REFERENCE_ITEM_NOT_FOUND"));
    }

    @Test
    void returns404ForUnknownItemAndUpdate() throws Exception {
        mockMvc.perform(get("/api/v1/reference/CURRENCY/ZZZ"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("REFERENCE_ITEM_NOT_FOUND"));

        mockMvc.perform(put("/api/v1/reference/does-not-exist")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"label\":\"不存在\",\"sortOrder\":1,\"active\":true}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("REFERENCE_ITEM_NOT_FOUND"));

        mockMvc.perform(delete("/api/v1/reference/does-not-exist"))
                .andExpect(status().isNotFound());
    }

    @Test
    void returnsEmptyListForUnknownDomain() throws Exception {
        mockMvc.perform(get("/api/v1/reference/NO_SUCH_DOMAIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void rejectsInvalidCreateRequest() throws Exception {
        mockMvc.perform(post("/api/v1/reference")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"domain\":\"CURRENCY\",\"code\":\"JPY\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        mockMvc.perform(post("/api/v1/reference")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"domain\":\"CURRENCY\",\"code\":\"JPY\",\"label\":\"日元\",\"sortOrder\":-1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }
}
