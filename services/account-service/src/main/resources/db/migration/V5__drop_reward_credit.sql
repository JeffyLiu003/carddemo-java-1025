-- Marketing capability removed: drops the cashback idempotency table (V3 stays as a historical migration to keep Flyway checksums intact)
DROP TABLE IF EXISTS reward_credit;
