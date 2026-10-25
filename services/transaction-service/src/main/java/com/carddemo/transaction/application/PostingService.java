package com.carddemo.transaction.application;

import com.carddemo.transaction.domain.model.AccountSnapshot;
import com.carddemo.transaction.domain.model.CardSnapshot;
import com.carddemo.transaction.domain.model.DailyTransaction;
import com.carddemo.transaction.domain.model.RejectReason;
import com.carddemo.transaction.domain.model.TranCategoryBalance;
import com.carddemo.transaction.domain.model.TransactionRecord;
import com.carddemo.transaction.domain.model.TransactionStatus;
import com.carddemo.transaction.domain.repository.DailyTransactionRepository;
import com.carddemo.transaction.domain.repository.TranCategoryBalanceRepository;
import com.carddemo.transaction.domain.repository.TransactionRepository;
import com.carddemo.transaction.domain.service.PostingPolicy;
import com.carddemo.transaction.infrastructure.client.AccountClient;
import com.carddemo.transaction.infrastructure.client.CardClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 日终入账用例（CBTRN02C）。单笔拒付不中断整批（BR-POST-05/08）；同一交易号重复提交按幂等返回（NR-POST-01）。
 */
@Service
@Transactional
public class PostingService {

    private static final BigDecimal ZERO = new BigDecimal("0.00");

    private final TransactionRepository transactions;
    private final DailyTransactionRepository dailyTransactions;
    private final TranCategoryBalanceRepository balances;
    private final TransactionIdGenerator idGenerator;
    private final CardClient cardClient;
    private final AccountClient accountClient;
    private final PostingPolicy postingPolicy;

    public PostingService(TransactionRepository transactions,
                          DailyTransactionRepository dailyTransactions,
                          TranCategoryBalanceRepository balances,
                          TransactionIdGenerator idGenerator,
                          CardClient cardClient,
                          AccountClient accountClient,
                          PostingPolicy postingPolicy) {
        this.transactions = transactions;
        this.dailyTransactions = dailyTransactions;
        this.balances = balances;
        this.idGenerator = idGenerator;
        this.cardClient = cardClient;
        this.accountClient = accountClient;
        this.postingPolicy = postingPolicy;
    }

    public PostingResult post(List<PostingCommand> commands) {
        List<PostingRejection> rejections = new ArrayList<>();
        List<String> duplicates = new ArrayList<>();
        int posted = 0;
        int rejected = 0;
        String generatedId = null;

        for (PostingCommand command : commands) {
            String transactionId = TransactionApplicationService.trimToNull(command.transactionId());
            if (transactionId != null && transactions.existsByTransactionId(transactionId)) {
                posted++;
                duplicates.add(transactionId);
                continue;
            }
            if (transactionId == null) {
                generatedId = generatedId == null ? idGenerator.next() : idGenerator.increment(generatedId);
                transactionId = generatedId;
            }

            LocalDateTime originalTimestamp = command.originalTimestamp() == null
                    ? LocalDateTime.now() : command.originalTimestamp();
            LocalDateTime processedTimestamp = LocalDateTime.now();

            CardSnapshot card = cardClient.findCard(command.cardNumber()).orElse(null);
            AccountSnapshot account = card == null ? null
                    : accountClient.findAccount(card.accountId()).orElse(null);

            Optional<RejectReason> rejection = postingPolicy.evaluate(card, account, command.amount(),
                    originalTimestamp.toLocalDate());
            if (rejection.isPresent()) {
                RejectReason reason = rejection.get();
                rejected++;
                rejections.add(new PostingRejection(transactionId, reason.getCode(), reason.getDescription()));
                dailyTransactions.saveDailyTransaction(DailyTransaction.rejected(transactionId,
                        command.cardNumber(), command.typeCode(), command.categoryCode(), command.source(),
                        command.description(), command.amount(), command.merchantId(), command.merchantName(),
                        command.merchantCity(), command.merchantZip(), originalTimestamp, processedTimestamp,
                        reason));
                continue;
            }

            TranCategoryBalance balance = balances
                    .find(account.accountId(), command.typeCode(), command.categoryCode())
                    .orElseGet(() -> new TranCategoryBalance(account.accountId(), command.typeCode(),
                            command.categoryCode(), ZERO));
            balance.apply(command.amount());
            balances.saveBalance(balance);

            accountClient.applyPosting(account.accountId(), command.amount());

            TransactionRecord record = new TransactionRecord(transactionId, account.accountId(),
                    command.cardNumber(), command.typeCode(), command.categoryCode(), command.source(),
                    command.description(), command.amount(), command.merchantId(), command.merchantName(),
                    command.merchantCity(), command.merchantZip(), originalTimestamp, processedTimestamp,
                    TransactionStatus.POSTED);
            transactions.saveTransaction(record);
            dailyTransactions.saveDailyTransaction(DailyTransaction.posted(record));
            posted++;
        }

        String status = rejected > 0 ? "COMPLETED_WITH_REJECTS" : "COMPLETED";
        return new PostingResult(status, commands.size(), posted, rejected, rejections, duplicates);
    }
}
