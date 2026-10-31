-- Marketing capability removed: account 2200 becomes a generic liability; the account number is kept so historical journal lines are unaffected
UPDATE ledger_account SET name = 'Other Payable' WHERE account_code = '2200';
