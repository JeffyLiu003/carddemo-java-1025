package com.carddemo.loan.domain.repository;

import com.carddemo.loan.domain.model.Loan;

import java.util.Optional;

/**
 * 贷款仓储端口。实现见 infrastructure.persistence.LoanJpaRepository。
 */
public interface LoanRepository {

    Loan saveLoan(Loan loan);

    Optional<Loan> findByLoanId(String loanId);

    Optional<Loan> findByApplicationId(String applicationId);
}
