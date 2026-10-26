package com.carddemo.audit.domain.model;

import com.carddemo.audit.domain.exception.InvalidRequestException;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuditEventTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 25, 10, 0);

    private AuditEventDraft draft(String actor, String action, String resourceType,
                                  String resourceId, String payload) {
        return new AuditEventDraft(actor, action, resourceType, resourceId, payload);
    }

    @Test
    void keepsSuppliedFieldsAndTrimsKeys() {
        AuditEvent event = new AuditEvent("evt-1",
                draft("  demo.operator ", " CARD_STATUS_CHANGED ", " CARD ", " 3344556677889900 ",
                        "{\"to\":\"BLOCKED\"}"), NOW);

        assertThat(event.getEventId()).isEqualTo("evt-1");
        assertThat(event.getActor()).isEqualTo("demo.operator");
        assertThat(event.getAction()).isEqualTo("CARD_STATUS_CHANGED");
        assertThat(event.getResourceType()).isEqualTo("CARD");
        assertThat(event.getResourceId()).isEqualTo("3344556677889900");
        assertThat(event.getPayload()).isEqualTo("{\"to\":\"BLOCKED\"}");
        assertThat(event.getCreatedAt()).isEqualTo(NOW);
    }

    @Test
    void payloadIsOptional() {
        AuditEvent event = new AuditEvent("evt-2",
                draft("demo", "LOGIN", "CUSTOMER", "000000001", "  "), NOW);

        assertThat(event.getPayload()).isNull();
    }

    @Test
    void rejectsMissingActor() {
        assertThatThrownBy(() -> new AuditEvent("evt-3",
                draft(null, "LOGIN", "CUSTOMER", "000000001", null), NOW))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("actor");
    }

    @Test
    void rejectsBlankRequiredFields() {
        assertThatThrownBy(() -> new AuditEvent("evt-4",
                draft("demo", " ", "CUSTOMER", "000000001", null), NOW))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("action");
        assertThatThrownBy(() -> new AuditEvent("evt-5",
                draft("demo", "LOGIN", null, "000000001", null), NOW))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("resourceType");
        assertThatThrownBy(() -> new AuditEvent("evt-6",
                draft("demo", "LOGIN", "CUSTOMER", "", null), NOW))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("resourceId");
    }

    @Test
    void rejectsOverlongActor() {
        assertThatThrownBy(() -> new AuditEvent("evt-7",
                draft("a".repeat(65), "LOGIN", "CUSTOMER", "000000001", null), NOW))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("64");
    }
}
