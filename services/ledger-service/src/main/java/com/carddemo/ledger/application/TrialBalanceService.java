package com.carddemo.ledger.application;

import com.carddemo.ledger.domain.model.Direction;
import com.carddemo.ledger.domain.model.LedgerAccount;
import com.carddemo.ledger.domain.repository.AccountActivity;
import com.carddemo.ledger.domain.repository.JournalEntryRepository;
import com.carddemo.ledger.domain.repository.LedgerAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 试算平衡：按期间的月初到月末（半开区间）过滤 posted_at，汇总每个科目的借贷合计与净额。
 * 已冲正的分录仍然计入——冲正分录以相反方向抵消，净额自然归零。
 */
@Service
@Transactional(readOnly = true)
public class TrialBalanceService {

    private static final BigDecimal ZERO = new BigDecimal("0.00");

    private final JournalEntryRepository entries;
    private final LedgerAccountRepository accounts;

    public TrialBalanceService(JournalEntryRepository entries, LedgerAccountRepository accounts) {
        this.entries = entries;
        this.accounts = accounts;
    }

    public TrialBalanceView trialBalance(YearMonth period) {
        LocalDateTime from = period.atDay(1).atStartOfDay();
        LocalDateTime to = period.plusMonths(1).atDay(1).atStartOfDay();

        Map<String, BigDecimal> debits = new TreeMap<>();
        Map<String, BigDecimal> credits = new TreeMap<>();
        for (AccountActivity activity : entries.aggregateByAccount(from, to)) {
            Map<String, BigDecimal> target = activity.direction() == Direction.DR ? debits : credits;
            target.merge(activity.accountCode(), activity.amount(), BigDecimal::add);
        }

        Map<String, LedgerAccount> accountsByCode = accounts.findAllAccounts().stream()
                .collect(Collectors.toMap(LedgerAccount::getAccountCode, Function.identity()));

        Set<String> codes = new TreeSet<>(debits.keySet());
        codes.addAll(credits.keySet());

        List<TrialBalanceItem> items = new ArrayList<>();
        BigDecimal totalDebit = ZERO;
        BigDecimal totalCredit = ZERO;
        for (String code : codes) {
            BigDecimal debit = debits.getOrDefault(code, ZERO);
            BigDecimal credit = credits.getOrDefault(code, ZERO);
            LedgerAccount account = accountsByCode.get(code);
            items.add(new TrialBalanceItem(
                    code,
                    account == null ? null : account.getName(),
                    account == null ? null : account.getAccountType().name(),
                    debit,
                    credit,
                    debit.subtract(credit)));
            totalDebit = totalDebit.add(debit);
            totalCredit = totalCredit.add(credit);
        }

        return new TrialBalanceView(period.toString(), totalDebit, totalCredit,
                totalDebit.compareTo(totalCredit) == 0, List.copyOf(items));
    }
}
