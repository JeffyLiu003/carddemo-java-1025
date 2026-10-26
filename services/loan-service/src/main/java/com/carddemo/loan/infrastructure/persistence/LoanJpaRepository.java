package com.carddemo.loan.infrastructure.persistence;

import com.carddemo.loan.domain.model.Loan;
import com.carddemo.loan.domain.repository.LoanRepository;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LoanJpaRepository extends JpaRepository<Loan, String>, LoanRepository {

    @Override
    default Loan saveLoan(Loan loan) {
        return saveAndFlush(loan);
    }

    @Override
    default Optional<Loan> findByLoanId(String loanId) {
        return findById(loanId);
    }

    @Override
    Optional<Loan> findByApplicationId(String applicationId);
}
