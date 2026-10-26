package com.carddemo.loan.infrastructure.persistence;

import com.carddemo.loan.domain.model.InstallmentStatus;
import com.carddemo.loan.domain.model.RepaymentSchedule;
import com.carddemo.loan.domain.repository.RepaymentScheduleRepository;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RepaymentScheduleJpaRepository
        extends JpaRepository<RepaymentSchedule, String>, RepaymentScheduleRepository {

    @Override
    default List<RepaymentSchedule> saveSchedules(List<RepaymentSchedule> schedules) {
        return saveAll(schedules);
    }

    @Override
    default List<RepaymentSchedule> findSchedulesByLoanId(String loanId) {
        return findAllByLoanId(loanId, Sort.by(Sort.Order.asc("installmentNo")));
    }

    @Override
    default List<RepaymentSchedule> findSchedulesByLoanIdAndStatus(String loanId, InstallmentStatus status) {
        return findAllByLoanIdAndStatus(loanId, status, Sort.by(Sort.Order.asc("installmentNo")));
    }

    List<RepaymentSchedule> findAllByLoanId(String loanId, Sort sort);

    List<RepaymentSchedule> findAllByLoanIdAndStatus(String loanId, InstallmentStatus status, Sort sort);
}
