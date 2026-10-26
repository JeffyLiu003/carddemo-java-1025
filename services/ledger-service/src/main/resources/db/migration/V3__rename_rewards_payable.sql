-- 营销能力移除：2200 科目改为通用负债，保留科目号避免影响历史分录
UPDATE ledger_account SET name = 'Other Payable' WHERE account_code = '2200';
