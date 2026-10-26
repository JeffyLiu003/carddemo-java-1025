-- 种子模板：还款到账 / 账单就绪（EMAIL）、验证码（SMS）（设计 §3.1）

INSERT INTO notification_template (template_code, channel, subject, body, active, version) VALUES
    ('PAYMENT_RECEIVED', 'EMAIL', '还款到账通知',
     '尊敬的 {{name}}：我们已收到您卡号 {{cardNumber}} 的还款 {{amount}}，当前欠款余额 {{balance}}。',
     TRUE, 0),
    ('STATEMENT_READY', 'EMAIL', '账单已生成',
     '尊敬的 {{name}}：您 {{period}} 账单已生成，本期最低还款 {{minimumPayment}}，到期日 {{dueDate}}。',
     TRUE, 0),
    ('OTP', 'SMS', NULL,
     '您的验证码是 {{code}}，{{expiresInMinutes}} 分钟内有效，请勿泄露。',
     TRUE, 0);
