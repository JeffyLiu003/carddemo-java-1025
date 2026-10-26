package com.carddemo.dispute.domain.repository;

import com.carddemo.dispute.domain.model.DisputeCase;

import java.util.Optional;

/**
 * 争议案件仓储端口。实现见 infrastructure.persistence.DisputeCaseJpaRepository。
 */
public interface DisputeCaseRepository {

    DisputeCase saveCase(DisputeCase disputeCase);

    Optional<DisputeCase> findByCaseId(String caseId);

    PageResult<DisputeCase> search(DisputeCaseFilter filter, int page, int size);
}
