-- Seed users from docs/design/05-api-catalog.md §0; passwords are PBKDF2-HMAC-SHA256 hashes (ADR-008) and must never be stored in clear text.
-- Clear text is for local demo only: ADMIN001 / admin123, USER0001 / user1234.
INSERT INTO sec_user (user_id, first_name, last_name, password_hash, user_type, version)
VALUES ('ADMIN001', 'System', 'Administrator',
        'pbkdf2$210000$n25aKv4JuPM8wKaNo/11KQ==$jz7ISZRkT3GCAw5Bp7Bz9qnh/f1X9fRUavGfTgJhrB8=',
        'A', 0),
       ('USER0001', 'Demo', 'User',
        'pbkdf2$210000$79MTjMtdOCgD2yOF5nN8dg==$HY//zJddGDTuCNvn51tENgtN/5C4ej/NJCfBG85OfeY=',
        'U', 0);
