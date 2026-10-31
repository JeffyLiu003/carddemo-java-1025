-- Reference data seed: currencies, countries, holidays, product categories
INSERT INTO reference_item (item_id, domain, code, label, sort_order, active, version)
VALUES ('a0000000-0000-0000-0000-000000000001', 'CURRENCY', 'USD', 'US Dollar', 10, TRUE, 0),
       ('a0000000-0000-0000-0000-000000000002', 'CURRENCY', 'CNY', 'Chinese Yuan', 20, TRUE, 0),
       ('a0000000-0000-0000-0000-000000000003', 'CURRENCY', 'EUR', 'Euro', 30, TRUE, 0),
       ('a0000000-0000-0000-0000-000000000004', 'COUNTRY', 'US', 'United States', 10, TRUE, 0),
       ('a0000000-0000-0000-0000-000000000005', 'COUNTRY', 'CN', 'China', 20, TRUE, 0),
       ('a0000000-0000-0000-0000-000000000006', 'COUNTRY', 'SG', 'Singapore', 30, TRUE, 0),
       ('a0000000-0000-0000-0000-000000000007', 'HOLIDAY', '2026-10-01', 'National Day', 10, TRUE, 0),
       ('a0000000-0000-0000-0000-000000000008', 'HOLIDAY', '2026-12-25', 'Christmas Day', 20, TRUE, 0),
       ('a0000000-0000-0000-0000-000000000009', 'PRODUCT_CATEGORY', 'CLASSIC', 'Classic Card', 10, TRUE, 0),
       ('a0000000-0000-0000-0000-00000000000a', 'PRODUCT_CATEGORY', 'GOLD', 'Gold Card', 20, TRUE, 0),
       ('a0000000-0000-0000-0000-00000000000b', 'PRODUCT_CATEGORY', 'PLATINUM', 'Platinum Card', 30, TRUE, 0);
