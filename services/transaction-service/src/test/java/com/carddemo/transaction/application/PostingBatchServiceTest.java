package com.carddemo.transaction.application;

import com.carddemo.transaction.domain.exception.BusinessRuleException;
import com.carddemo.transaction.domain.exception.InvalidRequestException;
import com.carddemo.transaction.domain.exception.NotFoundException;
import com.carddemo.transaction.domain.exception.RemoteServiceException;
import com.carddemo.transaction.domain.model.AccountSnapshot;
import com.carddemo.transaction.domain.model.PostingBatch;
import com.carddemo.transaction.domain.model.PostingLeg;
import com.carddemo.transaction.domain.model.PostingStatus;
import com.carddemo.transaction.domain.repository.PageResult;
import com.carddemo.transaction.domain.repository.PostingBatchRepository;
import com.carddemo.transaction.infrastructure.client.AccountClient;
import com.carddemo.transaction.infrastructure.client.LedgerClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 过账引擎用例：account-service / ledger-service 用 Mockito 替换，本地过账库为真实 H2 + Flyway 种子。
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PostingBatchServiceTest {

    private static final String ACCOUNT_ID = "00000000006";

    @Autowired
    private PostingBatchService postingBatchService;

    @Autowired
    private PostingBatchRepository postingBatches;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private AccountClient accountClient;

    @MockBean
    private LedgerClient ledgerClient;

    @BeforeEach
    void stubDownstream() {
        when(accountClient.findAccount(ACCOUNT_ID)).thenReturn(Optional.of(account("Y")));
        when(accountClient.applyPosting(eq(ACCOUNT_ID), any())).thenReturn(account("Y"));
        when(ledgerClient.recordJournalEntry(any())).thenReturn("JE-0001");
    }

    @Test
    void increasePostingCreatesBalancedLegsAndCreditsAccount() {
        PostingBatchView view = postingBatchService.create(command("post-0001", "CARD_PURCHASE", "25.00"));

        PostingBatch batch = view.batch();
        assertThat(batch.getStatus()).isEqualTo(PostingStatus.POSTED);
        assertThat(view.typeName()).isEqualTo("Credit card purchase");
        assertThat(batch.getAmount()).isEqualByComparingTo("25.00");
        assertThat(batch.getCurrency()).isEqualTo("USD");
        assertThat(legSummary(batch.getLegs())).containsExactly("DR:1100", "CR:2100");
        assertThat(batch.getLedgerEntryId()).isEqualTo("JE-0001");
        assertThat(postingBatches.findByBatchId(batch.getBatchId())).isPresent();
        verify(accountClient).applyPosting(ACCOUNT_ID, new BigDecimal("25.00"));
    }

    @Test
    void decreasePostingSendsNegativeAmountToAccountService() {
        PostingBatchView view = postingBatchService.create(command("post-0002", "PAYMENT", "100.00"));

        assertThat(legSummary(view.batch().getLegs())).containsExactly("DR:1000", "CR:1100");
        verify(accountClient).applyPosting(ACCOUNT_ID, new BigDecimal("-100.00"));
    }

    @Test
    void repeatedIdempotencyKeyReturnsOriginalBatch() {
        PostingBatchView first = postingBatchService.create(command("post-0003", "CARD_PURCHASE", "25.00"));
        PostingBatchView second = postingBatchService.create(command("post-0003", "CARD_PURCHASE", "25.00"));

        assertThat(second.batch().getBatchId()).isEqualTo(first.batch().getBatchId());
        assertThat(second.typeName()).isEqualTo("Credit card purchase");
        verify(accountClient, times(1)).applyPosting(ACCOUNT_ID, new BigDecimal("25.00"));
        assertThat(postingBatches.findByIdempotencyKey("post-0003")).isPresent();
    }

    @Test
    void idempotencyKeyWithDifferentAmountConflicts() {
        postingBatchService.create(command("post-0004", "CARD_PURCHASE", "25.00"));

        assertThatThrownBy(() -> postingBatchService.create(command("post-0004", "CARD_PURCHASE", "30.00")))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("IDEMPOTENCY_CONFLICT"));
        verify(accountClient, times(1)).applyPosting(ACCOUNT_ID, new BigDecimal("25.00"));
    }

    @Test
    void reversalRestoresAccountAndSwapsDirections() {
        PostingBatch original = postingBatchService
                .create(command("post-0005", "CARD_PURCHASE", "25.00")).batch();

        PostingBatchView reversed = postingBatchService.reverse(original.getBatchId());

        PostingBatch reversal = reversed.batch();
        assertThat(reversal.getBatchId()).isNotEqualTo(original.getBatchId());
        assertThat(reversal.getReversalOf()).isEqualTo(original.getBatchId());
        assertThat(reversal.getIdempotencyKey()).isEqualTo("reversal:" + original.getBatchId());
        assertThat(reversal.getStatus()).isEqualTo(PostingStatus.POSTED);
        assertThat(legSummary(reversal.getLegs())).containsExactly("CR:1100", "DR:2100");

        PostingBatch stored = postingBatches.findByBatchId(original.getBatchId()).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(PostingStatus.REVERSED);
        assertThat(stored.getReversedAt()).isNotNull();
        verify(accountClient).applyPosting(ACCOUNT_ID, new BigDecimal("25.00"));
        verify(accountClient).applyPosting(ACCOUNT_ID, new BigDecimal("-25.00"));
    }

    @Test
    void repeatedReversalReturnsExistingReversalBatch() {
        PostingBatch original = postingBatchService
                .create(command("post-0006", "PAYMENT", "40.00")).batch();

        PostingBatch first = postingBatchService.reverse(original.getBatchId()).batch();
        PostingBatch second = postingBatchService.reverse(original.getBatchId()).batch();

        assertThat(second.getBatchId()).isEqualTo(first.getBatchId());
        assertThat(postingBatches.findByReversalOf(original.getBatchId())).get()
                .extracting(PostingBatch::getBatchId).isEqualTo(first.getBatchId());
        verify(accountClient, times(1)).applyPosting(ACCOUNT_ID, new BigDecimal("-40.00"));
        verify(accountClient, times(1)).applyPosting(ACCOUNT_ID, new BigDecimal("40.00"));
    }

    @Test
    void reversalOfReversalIsNotAllowed() {
        PostingBatch original = postingBatchService
                .create(command("post-0007", "FEE", "15.00")).batch();
        PostingBatch reversal = postingBatchService.reverse(original.getBatchId()).batch();

        assertThatThrownBy(() -> postingBatchService.reverse(reversal.getBatchId()))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("REVERSAL_NOT_ALLOWED"));
    }

    @Test
    void ledgerFailureDoesNotBlockPosting() {
        when(ledgerClient.recordJournalEntry(any()))
                .thenThrow(new RemoteServiceException("ledger-service is unavailable"));

        PostingBatch batch = postingBatchService
                .create(command("post-0008", "CARD_PURCHASE", "25.00")).batch();

        assertThat(batch.getStatus()).isEqualTo(PostingStatus.POSTED);
        assertThat(batch.getLedgerEntryId()).isNull();
        assertThat(postingBatches.findByBatchId(batch.getBatchId())).isPresent();
        verify(accountClient).applyPosting(ACCOUNT_ID, new BigDecimal("25.00"));
    }

    @Test
    void resyncLedgerCompletesMissingJournal() {
        when(ledgerClient.recordJournalEntry(any()))
                .thenThrow(new RemoteServiceException("ledger-service is unavailable"))
                .thenReturn("JE-RESYNC");

        PostingBatch batch = postingBatchService
                .create(command("post-0008b", "CARD_PURCHASE", "25.00")).batch();
        assertThat(batch.getLedgerEntryId()).isNull();

        PostingBatch resynced = postingBatchService.resyncLedger(batch.getBatchId()).batch();

        assertThat(resynced.getLedgerEntryId()).isEqualTo("JE-RESYNC");
        assertThat(postingBatches.findByBatchId(batch.getBatchId()))
                .get().extracting(PostingBatch::getLedgerEntryId).isEqualTo("JE-RESYNC");
    }

    @Test
    void inactivePostingTypeIsRejected() {
        jdbcTemplate.update("update posting_type set active = false where type_code = 'FEE'");

        assertThatThrownBy(() -> postingBatchService.create(command("post-0009", "FEE", "15.00")))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("POSTING_TYPE_INACTIVE"));
        verify(accountClient, never()).applyPosting(any(), any());
        verify(accountClient, never()).findAccount(any());
    }

    @Test
    void unknownPostingTypeIsRejected() {
        assertThatThrownBy(() -> postingBatchService.create(command("post-0010", "NOPE", "15.00")))
                .isInstanceOfSatisfying(NotFoundException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("POSTING_TYPE_NOT_FOUND"));
        verify(accountClient, never()).applyPosting(any(), any());
    }

    @Test
    void unknownAccountIsRejected() {
        when(accountClient.findAccount("99999999999")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> postingBatchService.create(new CreatePostingBatchCommand(
                "post-0011", "CARD_PURCHASE", "99999999999", new BigDecimal("25.00"), "USD", "Purchase")))
                .isInstanceOfSatisfying(NotFoundException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("ACCOUNT_NOT_FOUND"));
        verify(accountClient, never()).applyPosting(any(), any());
    }

    @Test
    void inactiveAccountIsRejected() {
        when(accountClient.findAccount(ACCOUNT_ID)).thenReturn(Optional.of(account("N")));

        assertThatThrownBy(() -> postingBatchService.create(command("post-0012", "CARD_PURCHASE", "25.00")))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("ACCOUNT_INACTIVE"));
        verify(accountClient, never()).applyPosting(any(), any());
    }

    @Test
    void invalidAmountIsRejected() {
        for (String amount : List.of("0.00", "-5.00", "10.123")) {
            assertThatThrownBy(() -> postingBatchService.create(command("post-0013" + amount, "CARD_PURCHASE", amount)))
                    .isInstanceOfSatisfying(BusinessRuleException.class,
                            ex -> assertThat(ex.getCode()).isEqualTo("INVALID_POSTING_AMOUNT"));
        }
        verify(accountClient, never()).applyPosting(any(), any());
    }

    @Test
    void missingIdempotencyKeyIsRejected() {
        assertThatThrownBy(() -> postingBatchService.create(command("  ", "CARD_PURCHASE", "25.00")))
                .isInstanceOfSatisfying(InvalidRequestException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("IDEMPOTENCY_KEY_REQUIRED"));
    }

    @Test
    void unbalancedRulesAreRejected() {
        jdbcTemplate.update("insert into posting_type (type_code, name, description, account_effect, active, version)"
                + " values ('BROKEN', 'Broken', 'Two debits one credit', 'INCREASE', true, 0)");
        jdbcTemplate.update("insert into posting_rule (type_code, leg_no, account_role, direction,"
                + " ledger_account_code, description, version) values ('BROKEN', 1, 'CARD_RECEIVABLE', 'DR', '1100', null, 0)");
        jdbcTemplate.update("insert into posting_rule (type_code, leg_no, account_role, direction,"
                + " ledger_account_code, description, version) values ('BROKEN', 2, 'CASH', 'DR', '1000', null, 0)");
        jdbcTemplate.update("insert into posting_rule (type_code, leg_no, account_role, direction,"
                + " ledger_account_code, description, version) values ('BROKEN', 3, 'MERCHANT_PAYABLE', 'CR', '2100', null, 0)");

        assertThatThrownBy(() -> postingBatchService.create(command("post-0014", "BROKEN", "25.00")))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("UNBALANCED_POSTING"));
        verify(accountClient, never()).applyPosting(any(), any());
    }

    @Test
    void listsBatchesFilteredByAccountAndIdempotencyKey() {
        postingBatchService.create(command("post-0015", "CARD_PURCHASE", "25.00"));
        postingBatchService.create(command("post-0016", "PAYMENT", "10.00"));

        PageResult<PostingBatchView> all = postingBatchService.list(ACCOUNT_ID, null, 0, 10);
        assertThat(all.total()).isEqualTo(2);
        assertThat(all.items()).hasSize(2);

        PageResult<PostingBatchView> byKey = postingBatchService.list(ACCOUNT_ID, "post-0016", 0, 10);
        assertThat(byKey.total()).isEqualTo(1);
        assertThat(byKey.items()).singleElement()
                .satisfies(view -> assertThat(view.batch().getIdempotencyKey()).isEqualTo("post-0016"));

        PageResult<PostingBatchView> otherAccount = postingBatchService.list("00000000001", null, 0, 10);
        assertThat(otherAccount.total()).isZero();
    }

    private CreatePostingBatchCommand command(String idempotencyKey, String typeCode, String amount) {
        return new CreatePostingBatchCommand(idempotencyKey, typeCode, ACCOUNT_ID,
                new BigDecimal(amount), "USD", "Purchase at Corner Grocery");
    }

    private AccountSnapshot account(String activeStatus) {
        return new AccountSnapshot(ACCOUNT_ID, activeStatus, new BigDecimal("1940.00"),
                new BigDecimal("5000.00"), new BigDecimal("1020.00"), new BigDecimal("0.00"),
                new BigDecimal("0.00"), LocalDate.of(2028, 5, 20), "A000000000");
    }

    private static List<String> legSummary(List<PostingLeg> legs) {
        return legs.stream()
                .map(leg -> leg.getDirection().name() + ":" + leg.getLedgerAccountCode())
                .toList();
    }
}
