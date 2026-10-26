package com.carddemo.notification.interfaces.rest;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 通知 REST 契约：H2 + Flyway 种子模板 + MockMvc。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class NotificationControllerTest {

    private static final String PAYMENT_BODY = """
            {"templateCode":"PAYMENT_RECEIVED","recipient":"alice@example.com",
             "payload":{"name":"Alice","cardNumber":"4000000000000001","amount":"100.00","balance":"1900.00"}}
            """;
    private static final String FAILING_BODY = """
            {"templateCode":"PAYMENT_RECEIVED","recipient":"fail@example.com","payload":{"name":"Bob"}}
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String createNotification(String body) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("notificationId").asText();
    }

    @Test
    void listsSeedTemplates() throws Exception {
        mockMvc.perform(get("/api/v1/notification-templates"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(3))
                .andExpect(jsonPath("$.items[0].templateCode").value("OTP"))
                .andExpect(jsonPath("$.items[0].channel").value("SMS"))
                .andExpect(jsonPath("$.items[1].templateCode").value("PAYMENT_RECEIVED"))
                .andExpect(jsonPath("$.items[1].channel").value("EMAIL"))
                .andExpect(jsonPath("$.items[1].active").value(true))
                .andExpect(jsonPath("$.items[2].templateCode").value("STATEMENT_READY"))
                .andExpect(jsonPath("$.items[2].channel").value("EMAIL"));
    }

    @Test
    void createsTemplateThenRejectsDuplicateAndNotifiesWithIt() throws Exception {
        mockMvc.perform(post("/api/v1/notification-templates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"templateCode":"WELCOME","channel":"EMAIL","subject":"欢迎",
                                 "body":"尊敬的 {{name}}，欢迎使用 CardDemo。"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.templateCode").value("WELCOME"))
                .andExpect(jsonPath("$.channel").value("EMAIL"))
                .andExpect(jsonPath("$.active").value(true));

        mockMvc.perform(post("/api/v1/notification-templates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"templateCode":"WELCOME","channel":"EMAIL","subject":"欢迎","body":"Hi {{name}}"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TEMPLATE_EXISTS"))
                .andExpect(jsonPath("$.message").isNotEmpty());

        mockMvc.perform(post("/api/v1/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"templateCode":"WELCOME","recipient":"alice@example.com",
                                 "payload":{"name":"Alice"}}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SENT"))
                .andExpect(jsonPath("$.subject").value("欢迎"))
                .andExpect(jsonPath("$.body").value("尊敬的 Alice，欢迎使用 CardDemo。"))
                .andExpect(jsonPath("$.payload.name").value("Alice"));
    }

    @Test
    void createsNotificationReadsDetailResendsAndFiltersByStatus() throws Exception {
        String notificationId = createNotification(PAYMENT_BODY);

        mockMvc.perform(post("/api/v1/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PAYMENT_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.notificationId").isNotEmpty())
                .andExpect(jsonPath("$.templateCode").value("PAYMENT_RECEIVED"))
                .andExpect(jsonPath("$.channel").value("EMAIL"))
                .andExpect(jsonPath("$.status").value("SENT"))
                .andExpect(jsonPath("$.subject").value("还款到账通知"))
                .andExpect(jsonPath("$.body").value(
                        "尊敬的 Alice：我们已收到您卡号 4000000000000001 的还款 100.00，当前欠款余额 1900.00。"))
                .andExpect(jsonPath("$.sentAt").isNotEmpty())
                .andExpect(jsonPath("$.error").doesNotExist());

        mockMvc.perform(get("/api/v1/notifications/{notificationId}", notificationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notificationId").value(notificationId))
                .andExpect(jsonPath("$.status").value("SENT"))
                .andExpect(jsonPath("$.payload.name").value("Alice"));

        mockMvc.perform(post("/api/v1/notifications/{notificationId}/send", notificationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notificationId").value(notificationId))
                .andExpect(jsonPath("$.status").value("SENT"));

        mockMvc.perform(get("/api/v1/notifications")
                        .param("status", "SENT")
                        .param("page", "0")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.items[0].status").value("SENT"));
    }

    @Test
    void createsFailedNotificationForFailRecipientAndResendKeepsFailed() throws Exception {
        String notificationId = createNotification(FAILING_BODY);

        mockMvc.perform(get("/api/v1/notifications/{notificationId}", notificationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.error").value("Delivery to fail@example.com failed"));

        mockMvc.perform(post("/api/v1/notifications/{notificationId}/send", notificationId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.error").value("Delivery to fail@example.com failed"));

        mockMvc.perform(get("/api/v1/notifications").param("status", "FAILED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].notificationId").value(notificationId));
    }

    @Test
    void unknownTemplateAndNotificationReturn404() throws Exception {
        mockMvc.perform(post("/api/v1/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"templateCode":"NO_SUCH_TEMPLATE","recipient":"alice@example.com"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TEMPLATE_NOT_FOUND"));

        mockMvc.perform(get("/api/v1/notifications/{notificationId}", "missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOTIFICATION_NOT_FOUND"));

        mockMvc.perform(post("/api/v1/notifications/{notificationId}/send", "missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOTIFICATION_NOT_FOUND"));
    }

    @Test
    void rejectsRequestWithoutRequiredFieldsAndInvalidStatusFilter() throws Exception {
        mockMvc.perform(post("/api/v1/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"templateCode\":\"PAYMENT_RECEIVED\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details").isArray());

        mockMvc.perform(post("/api/v1/notification-templates")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"channel\":\"EMAIL\",\"body\":\"Hi\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        mockMvc.perform(get("/api/v1/notifications").param("status", "bogus"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_STATUS"));
    }

    @Test
    void paginatesNotificationsAcrossStatuses() throws Exception {
        createNotification(PAYMENT_BODY);
        createNotification(FAILING_BODY);

        MvcResult all = mockMvc.perform(get("/api/v1/notifications").param("page", "0").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.total").value(2))
                .andReturn();

        JsonNode items = objectMapper.readTree(all.getResponse().getContentAsString()).get("items");
        assertThat(items.get(0).get("status").asText()).isIn("SENT", "FAILED");

        mockMvc.perform(get("/api/v1/notifications").param("page", "1").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.total").value(2));
    }
}
