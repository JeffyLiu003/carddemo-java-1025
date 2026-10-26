package com.carddemo.loan.domain.service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 等额本息摊还计算（设计 §2.2）。
 * <p>
 * 月利率 {@code r = annualRate / 12}；月供 {@code M = P×r / (1 - (1+r)^-n)} 按 HALF_UP 保留 2 位；
 * 每期利息 = 剩余本金×r（HALF_UP 2 位），本金 = M − 利息；最后一期本金取剩余本金、利息按剩余本金计。
 * 中间计算使用 DECIMAL128，避免逐期利息累加误差提前出现。
 */
public final class AmortizationCalculator {

    private static final MathContext MC = MathContext.DECIMAL128;
    private static final int MONEY_SCALE = 2;

    private AmortizationCalculator() {
    }

    /**
     * 期次计划行；{@code totalDue} 为当期本金与利息之和（末期可能略大于标准月供）。
     */
    public record ScheduleLine(int installmentNo, LocalDate dueDate, BigDecimal principalDue,
                               BigDecimal interestDue, BigDecimal totalDue) {
    }

    public static BigDecimal monthlyPayment(BigDecimal principal, BigDecimal annualRate, int termMonths) {
        BigDecimal monthlyRate = monthlyRate(annualRate);
        if (monthlyRate.signum() == 0) {
            return principal.divide(BigDecimal.valueOf(termMonths), MONEY_SCALE, RoundingMode.HALF_UP);
        }
        BigDecimal growth = BigDecimal.ONE.add(monthlyRate).pow(termMonths, MC);
        BigDecimal discount = BigDecimal.ONE.subtract(BigDecimal.ONE.divide(growth, MC));
        return principal.multiply(monthlyRate).divide(discount, MONEY_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * 生成完整还款计划；{@code firstDueDate} 为第 1 期到期日，此后每月同日。
     */
    public static List<ScheduleLine> buildSchedule(BigDecimal principal, BigDecimal annualRate,
                                                   int termMonths, LocalDate firstDueDate) {
        BigDecimal monthlyRate = monthlyRate(annualRate);
        BigDecimal payment = monthlyPayment(principal, annualRate, termMonths);
        BigDecimal remaining = principal.setScale(MONEY_SCALE, RoundingMode.HALF_UP);

        List<ScheduleLine> lines = new ArrayList<>(termMonths);
        for (int installmentNo = 1; installmentNo <= termMonths; installmentNo++) {
            BigDecimal interest = remaining.multiply(monthlyRate).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
            BigDecimal principalDue = installmentNo == termMonths
                    ? remaining
                    : payment.subtract(interest);
            lines.add(new ScheduleLine(installmentNo, firstDueDate.plusMonths(installmentNo - 1L),
                    principalDue, interest, principalDue.add(interest)));
            remaining = remaining.subtract(principalDue);
        }
        return lines;
    }

    private static BigDecimal monthlyRate(BigDecimal annualRate) {
        return annualRate.divide(BigDecimal.valueOf(12), MC);
    }
}
