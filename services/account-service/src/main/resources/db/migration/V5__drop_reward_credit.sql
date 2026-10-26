-- 营销能力移除：删除返现入账幂等表（V3 为历史迁移，保留以维持 Flyway 校验和）
DROP TABLE IF EXISTS reward_credit;
