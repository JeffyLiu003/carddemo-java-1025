package com.carddemo.marketing.domain.model;

import com.carddemo.marketing.domain.exception.BusinessRuleException;
import com.carddemo.marketing.domain.exception.InvalidRequestException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * 活动聚合根（docs/design/17-premium-cashback-campaign.md §3.1）。
 *
 * <p>规则口径：
 * <ul>
 *   <li>PC-01：durationMonths 1–24，endDate = startDate + N 月 - 1 天；</li>
 *   <li>PC-02：返现率 0–1，单笔/客户上限与预算 &gt; 0，产品等级非空；</li>
 *   <li>PC-04：账户产品编码在 eligibleProductCodes（逗号分隔，大写）内；</li>
 *   <li>PC-09：预算在创建 PENDING 返现时预占，consumedBudget 只会增长；</li>
 *   <li>状态只允许 DRAFT/PAUSED → ACTIVE → PAUSED → ENDED 的受控迁移。</li>
 * </ul>
 */
@Entity
@Table(name = "campaign")
public class Campaign {

    public static final int MIN_DURATION_MONTHS = 1;
    public static final int MAX_DURATION_MONTHS = 24;
    public static final String INVALID_CAMPAIGN = "INVALID_CAMPAIGN";
    public static final String INVALID_TRANSITION = "CAMPAIGN_INVALID_TRANSITION";
    public static final String BUDGET_EXHAUSTED = "CAMPAIGN_BUDGET_EXHAUSTED";

    private static final BigDecimal ZERO = new BigDecimal("0.00");
    private static final BigDecimal ONE = BigDecimal.ONE;
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final int AMOUNT_SCALE = 2;
    private static final int RATE_SCALE = 6;
    private static final int PRODUCT_CODES_MAX_LENGTH = 255;

    @Id
    @Column(name = "campaign_id", length = 36, nullable = false, updatable = false)
    private String campaignId;

    @Column(name = "code", length = 64, nullable = false, updatable = false)
    private String code;

    @Column(name = "name", length = 128, nullable = false)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 16, nullable = false)
    private CampaignStatus status;

    @Column(name = "start_date", nullable = false, updatable = false)
    private LocalDate startDate;

    @Column(name = "duration_months", nullable = false, updatable = false)
    private int durationMonths;

    @Column(name = "end_date", nullable = false, updatable = false)
    private LocalDate endDate;

    @Column(name = "eligible_product_codes", length = PRODUCT_CODES_MAX_LENGTH, nullable = false,
            updatable = false)
    private String eligibleProductCodes;

    @Column(name = "min_transaction_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal minTransactionAmount;

    @Column(name = "cashback_rate", nullable = false, precision = 8, scale = 6)
    private BigDecimal cashbackRate;

    @Column(name = "max_cashback_per_transaction", nullable = false, precision = 14, scale = 2)
    private BigDecimal maxCashbackPerTransaction;

    @Column(name = "max_cashback_per_customer", nullable = false, precision = 14, scale = 2)
    private BigDecimal maxCashbackPerCustomer;

    @Column(name = "budget", nullable = false, precision = 14, scale = 2)
    private BigDecimal budget;

    @Column(name = "consumed_budget", nullable = false, precision = 14, scale = 2)
    private BigDecimal consumedBudget = ZERO;

    @Column(name = "enrollment_required", nullable = false)
    private boolean enrollmentRequired;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected Campaign() {
        // for JPA
    }

    /**
     * 创建活动：一律 DRAFT，等待 activate 发布；金额/费率按契约归一后落库。
     */
    public static Campaign create(String campaignId, CampaignTerms terms, LocalDateTime createdAt) {
        Objects.requireNonNull(terms, "terms must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Campaign campaign = new Campaign();
        campaign.campaignId = requireText(campaignId, "campaignId");
        campaign.code = requireCode(terms.code());
        campaign.name = requireLength(requireText(terms.name(), "name"), 128, "name");
        campaign.description = requireLength(terms.description(), 500, "description");
        campaign.status = CampaignStatus.DRAFT;
        campaign.startDate = requireStartDate(terms.startDate());
        campaign.durationMonths = requireDuration(terms.durationMonths());
        campaign.endDate = campaign.startDate.plusMonths(campaign.durationMonths).minusDays(1);
        campaign.eligibleProductCodes = requireProductCodes(terms.eligibleProductCodes());
        campaign.minTransactionAmount = requireAmount(terms.minTransactionAmount(), "minTransactionAmount", false);
        campaign.cashbackRate = requireRate(terms.cashbackRate());
        campaign.maxCashbackPerTransaction = requireAmount(terms.maxCashbackPerTransaction(),
                "maxCashbackPerTransaction", true);
        campaign.maxCashbackPerCustomer = requireAmount(terms.maxCashbackPerCustomer(),
                "maxCashbackPerCustomer", true);
        campaign.budget = requireAmount(terms.budget(), "budget", true);
        campaign.consumedBudget = ZERO;
        campaign.enrollmentRequired = terms.enrollmentRequired();
        campaign.createdAt = createdAt;
        campaign.updatedAt = createdAt;
        return campaign;
    }

    /**
     * 发布：DRAFT/PAUSED → ACTIVE。
     */
    public void activate(LocalDateTime now) {
        if (status != CampaignStatus.DRAFT && status != CampaignStatus.PAUSED) {
            throw new BusinessRuleException(INVALID_TRANSITION,
                    "Campaign " + code + " cannot be activated from " + status + ".");
        }
        status = CampaignStatus.ACTIVE;
        updatedAt = Objects.requireNonNull(now, "now must not be null");
    }

    /**
     * 暂停：ACTIVE → PAUSED。
     */
    public void pause(LocalDateTime now) {
        if (status != CampaignStatus.ACTIVE) {
            throw new BusinessRuleException(INVALID_TRANSITION,
                    "Campaign " + code + " cannot be paused from " + status + ".");
        }
        status = CampaignStatus.PAUSED;
        updatedAt = Objects.requireNonNull(now, "now must not be null");
    }

    /**
     * 结束：ACTIVE/PAUSED → ENDED。
     */
    public void end(LocalDateTime now) {
        if (status != CampaignStatus.ACTIVE && status != CampaignStatus.PAUSED) {
            throw new BusinessRuleException(INVALID_TRANSITION,
                    "Campaign " + code + " cannot be ended from " + status + ".");
        }
        status = CampaignStatus.ENDED;
        updatedAt = Objects.requireNonNull(now, "now must not be null");
    }

    /**
     * 产品圈选：逗号分隔的大写产品编码。
     */
    public List<String> productCodes() {
        return Arrays.stream(eligibleProductCodes.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(value -> value.toUpperCase(Locale.ROOT))
                .toList();
    }

    public boolean isProductEligible(String productCode) {
        if (productCode == null || productCode.isBlank()) {
            return false;
        }
        return productCodes().contains(productCode.trim().toUpperCase(Locale.ROOT));
    }

    /**
     * PC-03：交易日期落在 [startDate, endDate]（含边界）。
     */
    public boolean isWithinWindow(LocalDate date) {
        return date != null && !date.isBefore(startDate) && !date.isAfter(endDate);
    }

    public BigDecimal budgetRemaining() {
        return budget.subtract(consumedBudget).setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * PC-09：创建 PENDING 返现时预占预算，超出剩余预算直接拒绝，避免突破活动预算。
     */
    public void reserveBudget(BigDecimal cashbackAmount, LocalDateTime now) {
        BigDecimal cashback = Objects.requireNonNullElse(cashbackAmount, ZERO)
                .setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
        if (cashback.signum() <= 0) {
            return;
        }
        if (cashback.compareTo(budgetRemaining()) > 0) {
            throw new BusinessRuleException(BUDGET_EXHAUSTED,
                    "Campaign " + code + " has only " + budgetRemaining().toPlainString()
                            + " budget left, cashback " + cashback.toPlainString() + " was rejected.");
        }
        consumedBudget = consumedBudget.add(cashback);
        updatedAt = Objects.requireNonNull(now, "now must not be null");
    }

    /**
     * 给客户/前台展示的返现摘要。
     */
    public String rewardSummary() {
        BigDecimal ratePercent = cashbackRate.multiply(HUNDRED).setScale(2, RoundingMode.HALF_UP)
                .stripTrailingZeros();
        return "消费满 " + minTransactionAmount.toPlainString() + " 返 " + ratePercent.toPlainString()
                + "%，单笔上限 " + maxCashbackPerTransaction.toPlainString()
                + "，客户累计上限 " + maxCashbackPerCustomer.toPlainString();
    }

    private static String requireCode(String value) {
        String code = requireText(value, "code").toUpperCase(Locale.ROOT);
        if (code.length() > 64) {
            throw new InvalidRequestException(INVALID_CAMPAIGN, "Campaign code must not exceed 64 characters.");
        }
        return code;
    }

    private static LocalDate requireStartDate(LocalDate value) {
        if (value == null) {
            throw new InvalidRequestException(INVALID_CAMPAIGN, "Campaign startDate must be supplied.");
        }
        return value;
    }

    private static int requireDuration(int durationMonths) {
        if (durationMonths < MIN_DURATION_MONTHS || durationMonths > MAX_DURATION_MONTHS) {
            throw new InvalidRequestException(INVALID_CAMPAIGN,
                    "Campaign durationMonths must be between " + MIN_DURATION_MONTHS + " and "
                            + MAX_DURATION_MONTHS + ".");
        }
        return durationMonths;
    }

    private static String requireProductCodes(List<String> productCodes) {
        if (productCodes == null) {
            throw new InvalidRequestException(INVALID_CAMPAIGN, "Campaign eligibleProductCodes must be supplied.");
        }
        List<String> normalized = productCodes.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(value -> value.toUpperCase(Locale.ROOT))
                .distinct()
                .toList();
        if (normalized.isEmpty()) {
            throw new InvalidRequestException(INVALID_CAMPAIGN, "Campaign eligibleProductCodes must not be empty.");
        }
        String joined = String.join(",", normalized);
        if (joined.length() > PRODUCT_CODES_MAX_LENGTH) {
            throw new InvalidRequestException(INVALID_CAMPAIGN,
                    "Campaign eligibleProductCodes must not exceed " + PRODUCT_CODES_MAX_LENGTH + " characters.");
        }
        return joined;
    }

    private static BigDecimal requireRate(BigDecimal value) {
        if (value == null || value.signum() < 0 || value.compareTo(ONE) > 0) {
            throw new InvalidRequestException(INVALID_CAMPAIGN,
                    "Campaign cashbackRate must be between 0 and 1.");
        }
        return value.setScale(RATE_SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal requireAmount(BigDecimal value, String field, boolean mustBePositive) {
        if (value == null) {
            throw new InvalidRequestException(INVALID_CAMPAIGN, "Campaign " + field + " must be supplied.");
        }
        BigDecimal amount = value.setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
        boolean invalid = mustBePositive ? amount.signum() <= 0 : amount.signum() < 0;
        if (invalid) {
            throw new InvalidRequestException(INVALID_CAMPAIGN,
                    "Campaign " + field + (mustBePositive ? " must be greater than 0." : " must not be negative."));
        }
        return amount;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new InvalidRequestException(INVALID_CAMPAIGN, "Campaign " + field + " must be supplied.");
        }
        return value.trim();
    }

    private static String requireLength(String value, int maxLength, String field) {
        if (value != null && value.length() > maxLength) {
            throw new InvalidRequestException(INVALID_CAMPAIGN,
                    "Campaign " + field + " must not exceed " + maxLength + " characters.");
        }
        return value;
    }

    public String getCampaignId() {
        return campaignId;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public CampaignStatus getStatus() {
        return status;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public int getDurationMonths() {
        return durationMonths;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public String getEligibleProductCodes() {
        return eligibleProductCodes;
    }

    public BigDecimal getMinTransactionAmount() {
        return minTransactionAmount;
    }

    public BigDecimal getCashbackRate() {
        return cashbackRate;
    }

    public BigDecimal getMaxCashbackPerTransaction() {
        return maxCashbackPerTransaction;
    }

    public BigDecimal getMaxCashbackPerCustomer() {
        return maxCashbackPerCustomer;
    }

    public BigDecimal getBudget() {
        return budget;
    }

    public BigDecimal getConsumedBudget() {
        return consumedBudget;
    }

    public boolean isEnrollmentRequired() {
        return enrollmentRequired;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public long getVersion() {
        return version;
    }
}
