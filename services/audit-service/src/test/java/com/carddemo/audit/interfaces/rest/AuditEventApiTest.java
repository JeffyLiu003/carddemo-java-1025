package com.carddemo.audit.interfaces.rest;

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
import java.time.LocalDateTime;
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
class AuditEventApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private JsonNode body(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private JsonNode append(String actor, String action, String resourceType, String resourceId,
                            String payload) throws Exception {
        String request = objectMapper.createObjectNode()
                .put("actor", actor)
                .put("action", action)
                .put("resourceType", resourceType)
                .put("resourceId", resourceId)
                .put("payload", payload)
                .toString();
        MvcResult created = mockMvc.perform(post("/api/v1/audit-events")
                        .contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isCreated())
                .andReturn();
        return body(created);
    }

    @Test
    void appendsAndReadsBackDetail() throws Exception {
        String payload = "{\"from\":\"ACTIVE\",\"to\":\"BLOCKED\"}";
        JsonNode created = append("demo.operator", "CARD_STATUS_CHANGED", "CARD",
                "3344556677889900", payload);

        String eventId = created.get("eventId").asText();
        assertThat(eventId).isNotBlank();
        assertThat(created.get("actor").asText()).isEqualTo("demo.operator");
        assertThat(created.get("createdAt").asText()).isNotBlank();

        mockMvc.perform(get("/api/v1/audit-events/" + eventId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventId").value(eventId))
                .andExpect(jsonPath("$.actor").value("demo.operator"))
                .andExpect(jsonPath("$.action").value("CARD_STATUS_CHANGED"))
                .andExpect(jsonPath("$.resourceType").value("CARD"))
                .andExpect(jsonPath("$.resourceId").value("3344556677889900"))
                .andExpect(jsonPath("$.payload").value(payload));
    }

    @Test
    void appendsWithoutPayload() throws Exception {
        JsonNode created = append("demo.operator", "LOGIN", "CUSTOMER", "000000001", null);

        mockMvc.perform(get("/api/v1/audit-events/" + created.get("eventId").asText()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payload").doesNotExist());
    }

    @Test
    void searchesByActor() throws Exception {
        append("audit.actor.one", "LOGIN", "CUSTOMER", "000000001", null);
        append("audit.actor.one", "LOGOUT", "CUSTOMER", "000000001", null);
        append("audit.actor.two", "LOGIN", "CUSTOMER", "000000002", null);

        mockMvc.perform(get("/api/v1/audit-events").param("actor", "audit.actor.one"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].actor").value("audit.actor.one"))
                .andExpect(jsonPath("$.items[1].actor").value("audit.actor.one"));
    }

    @Test
    void searchesByResourceTypeAndIdCombination() throws Exception {
        append("audit.resource", "LIMIT_INCREASE", "ACCOUNT", "00000000009", null);
        append("audit.resource", "CARD_BLOCK", "CARD", "3344556677889911", null);
        append("audit.resource", "CARD_UNBLOCK", "CARD", "3344556677889911", null);
        append("audit.resource", "CARD_BLOCK", "CARD", "3344556677889922", null);

        mockMvc.perform(get("/api/v1/audit-events")
                        .param("resourceType", "CARD")
                        .param("resourceId", "3344556677889911"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.items[0].resourceId").value("3344556677889911"))
                .andExpect(jsonPath("$.items[1].resourceId").value("3344556677889911"));

        mockMvc.perform(get("/api/v1/audit-events")
                        .param("actor", "audit.resource")
                        .param("resourceType", "CARD"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(3));
    }

    @Test
    void pagesNewestFirst() throws Exception {
        List<String> payloads = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            String payload = "{\"seq\":" + i + "}";
            payloads.add(payload);
            append("audit.paging", "CARD_BLOCK", "CARD", "3344556677889933", payload);
        }

        MvcResult firstPage = mockMvc.perform(get("/api/v1/audit-events")
                        .param("actor", "audit.paging")
                        .param("page", "0")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.total").value(5))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andReturn();

        mockMvc.perform(get("/api/v1/audit-events")
                        .param("actor", "audit.paging")
                        .param("page", "2")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(5))
                .andExpect(jsonPath("$.items.length()").value(1));

        MvcResult allPage = mockMvc.perform(get("/api/v1/audit-events")
                        .param("actor", "audit.paging")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(5))
                .andReturn();

        List<LocalDateTime> timestamps = new ArrayList<>();
        List<String> seen = new ArrayList<>();
        for (JsonNode item : body(allPage).get("items")) {
            timestamps.add(LocalDateTime.parse(item.get("createdAt").asText()));
            seen.add(item.get("payload").asText());
        }
        assertThat(timestamps).isSortedAccordingTo((left, right) -> right.compareTo(left));
        assertThat(seen).containsExactlyInAnyOrderElementsOf(payloads);
        assertThat(body(firstPage).get("items").get(0).get("payload").asText()).isEqualTo(seen.get(0));
    }

    @Test
    void rejectsMissingRequiredFields() throws Exception {
        mockMvc.perform(post("/api/v1/audit-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actor\":\"demo.operator\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details.length()").value(3));

        mockMvc.perform(post("/api/v1/audit-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        mockMvc.perform(post("/api/v1/audit-events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actor\":\" \",\"action\":\"LOGIN\","
                                + "\"resourceType\":\"CUSTOMER\",\"resourceId\":\"000000001\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void returns404ForUnknownEvent() throws Exception {
        mockMvc.perform(get("/api/v1/audit-events/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("AUDIT_EVENT_NOT_FOUND"));
    }

    @Test
    void exposesNoUpdateOrDeleteRoute() throws Exception {
        JsonNode created = append("audit.appendonly", "LOGIN", "CUSTOMER", "000000003", null);
        String eventId = created.get("eventId").asText();

        mockMvc.perform(put("/api/v1/audit-events/" + eventId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actor\":\"tamper\"}"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));

        mockMvc.perform(delete("/api/v1/audit-events/" + eventId))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));

        mockMvc.perform(put("/api/v1/audit-events"))
                .andExpect(status().isMethodNotAllowed());

        // 事件仍在
        mockMvc.perform(get("/api/v1/audit-events/" + eventId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actor").value("audit.appendonly"));
    }
}
