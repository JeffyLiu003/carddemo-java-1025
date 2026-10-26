package com.carddemo.collections.application;

import com.carddemo.collections.domain.exception.BusinessRuleException;
import com.carddemo.collections.domain.exception.InvalidRequestException;
import com.carddemo.collections.domain.exception.NotFoundException;
import com.carddemo.collections.domain.model.CaseStatus;
import com.carddemo.collections.domain.model.DelinquencyCase;
import com.carddemo.collections.domain.repository.PageResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 催收用例：H2 + Flyway，覆盖建案、重算、承诺还款、结案、过滤分页与 404。
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class DelinquencyCaseServiceTest {

    @Autowired
    private DelinquencyCaseService cases;

    private static OpenDelinquencyCaseCommand command(String accountId, int daysPastDue, String amount) {
        return new OpenDelinquencyCaseCommand(accountId, daysPastDue, new BigDecimal(amount));
    }

    @Test
    void opensCaseWithComputedBucketAndReadsItBack() {
        DelinquencyCase created = cases.open(command("00000000006", 45, "1234.56"));

        assertThat(created.getCaseId()).isNotBlank();
        assertThat(created.getBucket().name()).isEqualTo("B2");
        assertThat(created.getStatus()).isEqualTo(CaseStatus.OPEN);

        DelinquencyCase loaded = cases.get(created.getCaseId());
        assertThat(loaded.getAccountId()).isEqualTo("00000000006");
        assertThat(loaded.getDaysPastDue()).isEqualTo(45);
        assertThat(loaded.getOutstandingAmount()).isEqualByComparingTo("1234.56");
        assertThat(loaded.getCreatedAt()).isNotNull();
    }

    @Test
    void recalculateUpdatesDaysPastDueAndBucket() {
        String caseId = cases.open(command("00000000006", 30, "500.00")).getCaseId();

        cases.recalculate(caseId, 61);

        DelinquencyCase reloaded = cases.get(caseId);
        assertThat(reloaded.getDaysPastDue()).isEqualTo(61);
        assertThat(reloaded.getBucket().name()).isEqualTo("B3");

        cases.recalculate(caseId, 91);
        assertThat(cases.get(caseId).getBucket().name()).isEqualTo("B4");
    }

    @Test
    void promiseToPayMarksPtpAndRejectsPastDates() {
        String caseId = cases.open(command("00000000006", 45, "500.00")).getCaseId();
        LocalDate promiseDate = LocalDate.now().plusDays(7);

        DelinquencyCase promised = cases.promiseToPay(caseId, promiseDate);

        assertThat(promised.getStatus()).isEqualTo(CaseStatus.PTP);
        assertThat(cases.get(caseId).getPromiseDate()).isEqualTo(promiseDate);

        assertThatThrownBy(() -> cases.promiseToPay(caseId, LocalDate.now().minusDays(1)))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("INVALID_PROMISE_DATE"));
    }

    @Test
    void resolveIsTerminal() {
        String caseId = cases.open(command("00000000006", 91, "500.00")).getCaseId();

        DelinquencyCase resolved = cases.resolve(caseId);
        assertThat(resolved.getStatus()).isEqualTo(CaseStatus.RESOLVED);
        assertThat(resolved.getResolvedAt()).isNotNull();

        assertThatThrownBy(() -> cases.resolve(caseId))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("CASE_RESOLVED"));
        assertThatThrownBy(() -> cases.recalculate(caseId, 120))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("CASE_RESOLVED"));
        assertThatThrownBy(() -> cases.promiseToPay(caseId, LocalDate.now().plusDays(3)))
                .isInstanceOfSatisfying(BusinessRuleException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("CASE_RESOLVED"));

        DelinquencyCase unchanged = cases.get(caseId);
        assertThat(unchanged.getStatus()).isEqualTo(CaseStatus.RESOLVED);
        assertThat(unchanged.getDaysPastDue()).isEqualTo(91);
    }

    @Test
    void filtersAndPaginatesCases() {
        String b1 = cases.open(command("00000000001", 10, "100.00")).getCaseId();
        cases.open(command("00000000002", 45, "200.00"));
        cases.open(command("00000000003", 95, "300.00"));
        cases.resolve(b1);

        PageResult<DelinquencyCase> firstPage = cases.list(null, null, 0, 2);
        assertThat(firstPage.items()).hasSize(2);
        assertThat(firstPage.total()).isEqualTo(3);

        PageResult<DelinquencyCase> secondPage = cases.list(null, null, 1, 2);
        assertThat(secondPage.items()).hasSize(1);
        assertThat(secondPage.total()).isEqualTo(3);

        assertThat(cases.list("RESOLVED", null, 0, 20).items())
                .extracting(DelinquencyCase::getAccountId)
                .containsExactly("00000000001");
        assertThat(cases.list("open", null, 0, 20).items()).hasSize(2);
        assertThat(cases.list(null, "b2", 0, 20).items())
                .extracting(DelinquencyCase::getAccountId)
                .containsExactly("00000000002");
        assertThat(cases.list("OPEN", "B4", 0, 20).total()).isEqualTo(1);

        assertThatThrownBy(() -> cases.list("CLOSED", null, 0, 20))
                .isInstanceOfSatisfying(InvalidRequestException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("INVALID_STATUS"));
        assertThatThrownBy(() -> cases.list(null, "B9", 0, 20))
                .isInstanceOfSatisfying(InvalidRequestException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("INVALID_BUCKET"));
    }

    @Test
    void unknownCaseReturns404() {
        assertThatThrownBy(() -> cases.get("missing-case"))
                .isInstanceOfSatisfying(NotFoundException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo("DELINQUENCY_CASE_NOT_FOUND"));
        assertThatThrownBy(() -> cases.recalculate("missing-case", 30))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> cases.promiseToPay("missing-case", LocalDate.now().plusDays(1)))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> cases.resolve("missing-case"))
                .isInstanceOf(NotFoundException.class);
    }
}
