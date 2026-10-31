-- Seed templates: payment received / statement ready (EMAIL), verification code (SMS) (design §3.1)

INSERT INTO notification_template (template_code, channel, subject, body, active, version) VALUES
    ('PAYMENT_RECEIVED', 'EMAIL', 'Payment received',
     'Dear {{name}}: we have received your payment of {{amount}} on card {{cardNumber}}. Your outstanding balance is {{balance}}.',
     TRUE, 0),
    ('STATEMENT_READY', 'EMAIL', 'Statement ready',
     'Dear {{name}}: your {{period}} statement is ready. Minimum payment {{minimumPayment}}, due date {{dueDate}}.',
     TRUE, 0),
    ('OTP', 'SMS', NULL,
     'Your verification code is {{code}}. It is valid for {{expiresInMinutes}} minutes; do not share it.',
     TRUE, 0);
