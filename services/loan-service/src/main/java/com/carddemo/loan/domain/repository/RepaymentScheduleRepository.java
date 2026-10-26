package com.carddemo.loan.domain.repository;

import com.carddemo.loan.domain.model.InstallmentStatus;
import com.carddemo.loan.domain.model.RepaymentSchedule;

import java.util.List;

/**
 * 还款计划仓储端口。实现见 infrastructure.persistence.RepaymentScheduleJpaRepository。
 */
public interface RepaymentScheduleRepository {

    List<RepaymentSchedule> saveSchedules(List<RepaymentSchedule> schedules);

    List<RepaymentSchedule> findSchedulesByLoanId(String loanId);

    List<RepaymentSchedule> findSchedulesByLoanIdAndStatus(String loanId, InstallmentStatus status);
}
