package com.carddemo.notification.application;

import com.carddemo.notification.domain.exception.BusinessRuleException;
import com.carddemo.notification.domain.exception.InvalidRequestException;
import com.carddemo.notification.domain.exception.NotFoundException;
import com.carddemo.notification.domain.model.Channel;
import com.carddemo.notification.domain.model.Notification;
import com.carddemo.notification.domain.model.NotificationStatus;
import com.carddemo.notification.domain.model.NotificationTemplate;
import com.carddemo.notification.domain.repository.PageResult;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 通知用例：真实 H2 + Flyway 种子模板；事务回滚保证用例间数据隔离。
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class NotificationServiceTest {

    private static final String PAYMENT = "PAYMENT_RECEIVED";
    private static final String OTP = "OTP";
    private static final String ALICE = "alice@example.com";
    private static final String FAILING = "fail@example.com";

    @Autowired
    private NotificationService notifications;

    @Autowired
    private EntityManager entityManager;

    private NotificationView create(String templateCode, String recipient, Map<String, String> payload) {
        return notifications.create(new CreateNotificationCommand(templateCode, recipient, payload));
    }

    @Test
    void createsNotificationFromSeedTemplateRendersBodyAndMarksSent() {
        NotificationView view = create(PAYMENT, ALICE, Map.of(
                "name", "Alice",
                "cardNumber", "4000000000000001",
                "amount", "100.00",
                "balance", "1900.00"));

        assertThat(view.notification().getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(view.notification().getSentAt()).isNotNull();
        assertThat(view.notification().getError()).isNull();
        assertThat(view.notification().getChannel()).isEqualTo(Channel.EMAIL);
        assertThat(view.subject()).isEqualTo("Payment received");
        assertThat(view.body()).isEqualTo(
                "Dear Alice: we have received your payment of 100.00 on card 4000000000000001. Your outstanding balance is 1900.00.");
        assertThat(view.payload()).containsEntry("name", "Alice");

        NotificationView reloaded = notifications.get(view.notification().getNotificationId());
        assertThat(reloaded.body()).isEqualTo(view.body());
        assertThat(reloaded.notification().getStatus()).isEqualTo(NotificationStatus.SENT);
    }

    @Test
    void rendersMissingVariablesAsEmptyStringWhenPayloadIncomplete() {
        NotificationView view = create(PAYMENT, ALICE, Map.of("name", "Alice"));

        assertThat(view.body()).doesNotContain("{{").contains("Dear Alice:");
        assertThat(view.body()).isEqualTo("Dear Alice: we have received your payment of  on card . Your outstanding balance is .");
    }

    @Test
    void createsSmsNotificationFromOtpTemplate() {
        NotificationView view = create(OTP, "+6588887777", Map.of("code", "123456", "expiresInMinutes", "5"));

        assertThat(view.notification().getChannel()).isEqualTo(Channel.SMS);
        assertThat(view.subject()).isEmpty();
        assertThat(view.body()).isEqualTo("Your verification code is 123456. It is valid for 5 minutes; do not share it.");
        assertThat(view.notification().getStatus()).isEqualTo(NotificationStatus.SENT);
    }

    @Test
    void failsDeliveryForRecipientContainingFail() {
        NotificationView view = create(PAYMENT, FAILING, Map.of("name", "Bob"));

        assertThat(view.notification().getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(view.notification().getSentAt()).isNotNull();
        assertThat(view.notification().getError()).contains(FAILING);
        assertThat(view.body()).contains("Bob");
    }

    @Test
    void resendingIsIdempotentForSentAndFailedNotifications() {
        NotificationView sent = create(PAYMENT, ALICE, Map.of("name", "Alice"));
        NotificationView sentAgain = notifications.send(sent.notification().getNotificationId());

        assertThat(sentAgain.notification().getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(sentAgain.notification().getSentAt()).isEqualTo(sent.notification().getSentAt());
        assertThat(sentAgain.notification().getError()).isNull();

        NotificationView failed = create(PAYMENT, FAILING, Map.of("name", "Bob"));
        NotificationView failedAgain = notifications.send(failed.notification().getNotificationId());

        assertThat(failedAgain.notification().getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(failedAgain.notification().getSentAt()).isEqualTo(failed.notification().getSentAt());
        assertThat(failedAgain.notification().getError()).isEqualTo(failed.notification().getError());
    }

    @Test
    void createsTemplateAndNotifiesWithIt() {
        NotificationTemplate template = notifications.createTemplate(new CreateTemplateCommand(
                "WELCOME", Channel.EMAIL, "欢迎", "尊敬的 {{name}}，欢迎使用 CardDemo。"));

        assertThat(template.getTemplateCode()).isEqualTo("WELCOME");
        assertThat(template.isActive()).isTrue();

        NotificationView view = create("WELCOME", ALICE, Map.of("name", "Alice"));
        assertThat(view.body()).isEqualTo("尊敬的 Alice，欢迎使用 CardDemo。");
        assertThat(view.subject()).isEqualTo("欢迎");
    }

    @Test
    void rejectsDuplicateTemplateCode() {
        notifications.createTemplate(new CreateTemplateCommand("WELCOME", Channel.EMAIL, "欢迎", "Hi {{name}}"));

        assertThatThrownBy(() -> notifications.createTemplate(
                new CreateTemplateCommand("WELCOME", Channel.EMAIL, "重复", "Hi {{name}}")))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code").isEqualTo("TEMPLATE_EXISTS");
    }

    @Test
    void rejectsUnknownTemplate() {
        assertThatThrownBy(() -> create("NO_SUCH_TEMPLATE", ALICE, Map.of()))
                .isInstanceOf(NotFoundException.class)
                .extracting("code").isEqualTo("TEMPLATE_NOT_FOUND");
    }

    @Test
    void rejectsInactiveTemplate() {
        inactiveTemplate("RETIRED", "退订模板");

        assertThatThrownBy(() -> create("RETIRED", ALICE, Map.of()))
                .isInstanceOf(BusinessRuleException.class)
                .extracting("code").isEqualTo("TEMPLATE_INACTIVE");
        assertThat(notifications.list(null, 0, 10).total()).isZero();
    }

    @Test
    void rejectsUnknownNotification() {
        assertThatThrownBy(() -> notifications.get("missing"))
                .isInstanceOf(NotFoundException.class)
                .extracting("code").isEqualTo("NOTIFICATION_NOT_FOUND");
        assertThatThrownBy(() -> notifications.send("missing"))
                .isInstanceOf(NotFoundException.class)
                .extracting("code").isEqualTo("NOTIFICATION_NOT_FOUND");
    }

    @Test
    void listsNotificationsByStatusWithPagination() {
        create(PAYMENT, "alice@example.com", Map.of("name", "Alice"));
        create(PAYMENT, "bob@example.com", Map.of("name", "Bob"));
        create(PAYMENT, FAILING, Map.of("name", "Carol"));

        assertThat(notifications.list(null, 0, 10).total()).isEqualTo(3);

        PageResult<Notification> sent = notifications.list("SENT", 0, 1);
        assertThat(sent.total()).isEqualTo(2);
        assertThat(sent.items()).hasSize(1);
        assertThat(sent.items().get(0).getStatus()).isEqualTo(NotificationStatus.SENT);

        assertThat(notifications.list("sent", 0, 10).total()).isEqualTo(2);
        assertThat(notifications.list("FAILED", 0, 10).items())
                .extracting(Notification::getStatus).containsOnly(NotificationStatus.FAILED);
        assertThat(notifications.list("PENDING", 0, 10).total()).isZero();
        assertThatThrownBy(() -> notifications.list("bogus", 0, 10))
                .isInstanceOf(InvalidRequestException.class)
                .extracting("code").isEqualTo("INVALID_STATUS");
    }

    @Test
    void sendsPendingNotificationAndMarksItSent() {
        pendingNotification("pending-1");

        NotificationView view = notifications.send("pending-1");

        assertThat(view.notification().getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(view.notification().getSentAt()).isNotNull();
        assertThat(view.body()).contains("Alice");
        assertThat(notifications.list("PENDING", 0, 10).total()).isZero();
    }

    private void inactiveTemplate(String templateCode, String subject) {
        entityManager.createNativeQuery("""
                        INSERT INTO notification_template (template_code, channel, subject, body, active, version)
                        VALUES (:code, 'EMAIL', :subject, 'body', FALSE, 0)
                        """)
                .setParameter("code", templateCode)
                .setParameter("subject", subject)
                .executeUpdate();
    }

    private void pendingNotification(String notificationId) {
        entityManager.createNativeQuery("""
                        INSERT INTO notification (notification_id, template_code, recipient, channel, payload,
                                                  status, created_at, version)
                        VALUES (:id, 'PAYMENT_RECEIVED', 'alice@example.com', 'EMAIL', '{"name":"Alice"}',
                                'PENDING', CURRENT_TIMESTAMP, 0)
                        """)
                .setParameter("id", notificationId)
                .executeUpdate();
    }
}
