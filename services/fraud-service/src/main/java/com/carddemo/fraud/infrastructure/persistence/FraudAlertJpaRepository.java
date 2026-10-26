package com.carddemo.fraud.infrastructure.persistence;

import com.carddemo.fraud.domain.model.FraudAlert;
import com.carddemo.fraud.domain.model.FraudAlertStatus;
import com.carddemo.fraud.domain.repository.FraudAlertRepository;
import com.carddemo.fraud.domain.repository.PageResult;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FraudAlertJpaRepository
        extends JpaRepository<FraudAlert, String>, FraudAlertRepository {

    @Override
    default FraudAlert saveAlert(FraudAlert alert) {
        return save(alert);
    }

    @Override
    default Optional<FraudAlert> findAlert(String alertId) {
        return findById(alertId);
    }

    @Override
    default Optional<FraudAlert> findAlertByEvaluation(String evaluationId) {
        return findFirstByEvaluationIdOrderByCreatedAtDesc(evaluationId);
    }

    @Override
    default PageResult<FraudAlert> findAlerts(FraudAlertStatus status, String accountId,
                                              int page, int size) {
        Pageable pageable = PageRequest.of(page, size,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("alertId")));
        Page<FraudAlert> result;
        if (status == null && accountId == null) {
            result = findAll(pageable);
        } else if (status == null) {
            result = findAllByAccountId(accountId, pageable);
        } else if (accountId == null) {
            result = findAllByStatus(status, pageable);
        } else {
            result = findAllByStatusAndAccountId(status, accountId, pageable);
        }
        return new PageResult<>(result.getContent(), result.getTotalElements());
    }

    Page<FraudAlert> findAllByStatus(FraudAlertStatus status, Pageable pageable);

    Optional<FraudAlert> findFirstByEvaluationIdOrderByCreatedAtDesc(String evaluationId);

    Page<FraudAlert> findAllByAccountId(String accountId, Pageable pageable);

    Page<FraudAlert> findAllByStatusAndAccountId(FraudAlertStatus status, String accountId,
                                                 Pageable pageable);
}
