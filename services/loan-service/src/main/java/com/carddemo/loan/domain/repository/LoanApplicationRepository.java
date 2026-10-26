package com.carddemo.loan.domain.repository;

import com.carddemo.loan.domain.model.LoanApplication;
import com.carddemo.loan.domain.model.LoanApplicationStatus;

import java.util.Optional;

/**
 * 贷款申请仓储端口。实现见 infrastructure.persistence.LoanApplicationJpaRepository。
 */
public interface LoanApplicationRepository {

    LoanApplication saveApplication(LoanApplication application);

    Optional<LoanApplication> findByApplicationId(String applicationId);

    PageResult<LoanApplication> search(LoanApplicationStatus status, int page, int size);
}
