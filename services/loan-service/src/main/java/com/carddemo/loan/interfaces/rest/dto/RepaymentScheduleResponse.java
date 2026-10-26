package com.carddemo.loan.interfaces.rest.dto;

import com.carddemo.loan.domain.model.RepaymentSchedule;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 还款期次响应。
 */
public record RepaymentScheduleResponse(
        String installmentId,
        int installmentNo,
        LocalDate dueDate,
        BigDecimal principalDue,
        BigDecimal interestDue,
        BigDecimal totalDue,
        String status,
        LocalDateTime paidAt) {

    public static RepaymentScheduleResponse from(RepaymentSchedule schedule) {
        return new RepaymentScheduleResponse(
                schedule.getInstallmentId(),
                schedule.getInstallmentNo(),
                schedule.getDueDate(),
                schedule.getPrincipalDue(),
                schedule.getInterestDue(),
                schedule.getTotalDue(),
                schedule.getStatus().name(),
                schedule.getPaidAt());
    }
}
