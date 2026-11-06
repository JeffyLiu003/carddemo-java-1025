package com.carddemo.marketing.application;

import com.carddemo.marketing.domain.exception.InvalidRequestException;
import com.carddemo.marketing.domain.exception.NotFoundException;
import com.carddemo.marketing.domain.exception.RemoteServiceException;
import com.carddemo.marketing.domain.model.AccountPosition;
import com.carddemo.marketing.domain.model.Campaign;
import com.carddemo.marketing.domain.model.CampaignStatus;
import com.carddemo.marketing.domain.model.RewardCalculator;
import com.carddemo.marketing.domain.model.RewardGrant;
import com.carddemo.marketing.domain.model.SkipReason;
import com.carddemo.marketing.domain.repository.CampaignRepository;
import com.carddemo.marketing.domain.repository.EnrollmentRepository;
import com.carddemo.marketing.domain.repository.PageResult;
import com.carddemo.marketing.domain.repository.RewardGrantRepository;
import com.carddemo.marketing.infrastructure.client.AccountClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 返现用例：交易评估、返现记录、待入账重试（设计 §3.3 PC-03～PC-10）。
 *
 * <p>事务边界：评估故意不加类级 {@code @Transactional}——返现与贷记是两次独立提交，
 * 贷记失败只把 PENDING + failureReason 落库，不让资金事实（交易入账）被回滚。
 */
@Service
public class RewardService {

    private static final Logger log = LoggerFactory.getLogger(RewardService.class);
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int AMOUNT_SCALE = 2;

    private final CampaignRepository campaigns;
    private final EnrollmentRepository enrollments;
    private final RewardGrantRepository grants;
    private final AccountClient accounts;

    public RewardService(CampaignRepository campaigns, EnrollmentRepository enrollments,
                         RewardGrantRepository grants, AccountClient accounts) {
        this.campaigns = campaigns;
        this.enrollments = enrollments;
        this.grants = grants;
        this.accounts = accounts;
    }

    /**
     * 交易返现评估：只评估 ACTIVE 活动；命中则创建 PENDING 返现并预占预算，随后同步贷记账户。
     * 贷记失败不抛整体错误，返回 PENDING 记录供重试。
     */
    public EvaluationResult evaluate(EvaluateRewardCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        String transactionId = requireText(command.transactionId(), "INVALID_TRANSACTION_ID",
                "Transaction id must be supplied.");
        String accountId = requireText(command.accountId(), "INVALID_ACCOUNT_ID",
                "Account id must be supplied.");
        String customerId = requireText(command.customerId(), "INVALID_CUSTOMER_ID",
                "Customer id must be supplied.");
        LocalDateTime occurredAt = requireOccurredAt(command.occurredAt());
        BigDecimal amount = requireAmount(command.amount());
        AccountPosition position = accounts.creditPosition(accountId);
        LocalDate occurredOn = occurredAt.toLocalDate();

        List<EvaluationResult.Grant> granted = new ArrayList<>();
        List<EvaluationResult.Skip> skipped = new ArrayList<>();
        for (Campaign campaign : campaigns.findByStatus(CampaignStatus.ACTIVE)) {
            SkipReason reason = skipReason(campaign, transactionId, accountId, customerId, position,
                    occurredOn, amount);
            if (reason != null) {
                skipped.add(skip(campaign, reason));
                continue;
            }
            BigDecimal cashback = RewardCalculator.cashback(amount, campaign.getCashbackRate(),
                    campaign.getMaxCashbackPerTransaction(),
                    customerRemaining(campaign, customerId),
                    campaign.budgetRemaining());
            if (cashback.signum() <= 0) {
                skipped.add(skip(campaign, SkipReason.AMOUNT_TOO_LOW));
                continue;
            }
            LocalDateTime now = LocalDateTime.now();
            RewardGrant pending = RewardGrant.pending(UUID.randomUUID().toString(), campaign.getCampaignId(),
                    transactionId, accountId, command.cardNumber(), customerId, amount, cashback, now);
            campaign.reserveBudget(cashback, now);
            campaigns.save(campaign);
            RewardGrant saved = grants.save(pending);
            granted.add(EvaluationResult.Grant.of(credit(saved, campaign), campaign.getCode()));
        }
        return new EvaluationResult(granted, skipped);
    }

    @Transactional(readOnly = true)
    public PageResult<RewardView> list(String accountId, String customerId, int page, int size) {
        PageResult<RewardGrant> result = grants.search(trimToNull(accountId), trimToNull(customerId),
                Math.max(page, 0), size <= 0 ? DEFAULT_PAGE_SIZE : size);
        Map<String, String> codes = campaignCodes(result.items());
        return new PageResult<>(
                result.items().stream()
                        .map(grant -> RewardView.of(grant, codes.get(grant.getCampaignId())))
                        .toList(),
                result.total());
    }

    /**
     * 待入账重试：成功 → GRANTED；重复重试幂等（GRANTED 直接返回，不再贷记）。贷记仍失败则保留 PENDING。
     */
    public RewardView retry(String rewardId) {
        RewardGrant grant = grants.findById(requireText(rewardId, "INVALID_REWARD_ID", "Reward id must be supplied."))
                .orElseThrow(() -> new NotFoundException("REWARD_NOT_FOUND",
                        "Reward " + rewardId + " not found"));
        Campaign campaign = campaigns.findById(grant.getCampaignId())
                .orElseThrow(() -> new NotFoundException("CAMPAIGN_NOT_FOUND",
                        "Campaign " + grant.getCampaignId() + " not found"));
        if (!grant.isRetryable()) {
            return RewardView.of(grant, campaign.getCode());
        }
        return RewardView.of(credit(grant, campaign), campaign.getCode());
    }

    /**
     * 逐条判定跳过原因，顺序与设计 §3.3 的 PC-03 → PC-08 一致。
     */
    private SkipReason skipReason(Campaign campaign, String transactionId, String accountId, String customerId,
                                  AccountPosition position, LocalDate occurredOn, BigDecimal amount) {
        if (grants.findByCampaignIdAndTransactionId(campaign.getCampaignId(), transactionId).isPresent()) {
            return SkipReason.ALREADY_EVALUATED;
        }
        if (!campaign.isWithinWindow(occurredOn)) {
            return SkipReason.OUTSIDE_WINDOW;
        }
        if (!campaign.isProductEligible(position.productCode())) {
            return SkipReason.PRODUCT_NOT_ELIGIBLE;
        }
        if (campaign.isEnrollmentRequired()
                && !enrollments.existsByCampaignIdAndAccountId(campaign.getCampaignId(), accountId)) {
            return SkipReason.NOT_ENROLLED;
        }
        if (amount.compareTo(campaign.getMinTransactionAmount()) < 0) {
            return SkipReason.AMOUNT_TOO_LOW;
        }
        if (customerRemaining(campaign, customerId).signum() <= 0) {
            return SkipReason.CUSTOMER_CAP_REACHED;
        }
        if (campaign.budgetRemaining().signum() <= 0) {
            return SkipReason.BUDGET_EXHAUSTED;
        }
        return null;
    }

    private BigDecimal customerRemaining(Campaign campaign, String customerId) {
        BigDecimal used = grants.customerCashback(campaign.getCampaignId(), customerId);
        return campaign.getMaxCashbackPerCustomer().subtract(used).setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * 贷记账户（creditId = rewardId，账户侧幂等）：成功置 GRANTED；下游不可用/被拒绝时保留 PENDING 与失败原因。
     */
    private RewardGrant credit(RewardGrant grant, Campaign campaign) {
        try {
            accounts.credit(grant.getAccountId(), grant.getRewardId(), grant.getCashbackAmount(),
                    campaign.getCode() + " cashback");
            grant.markGranted(LocalDateTime.now());
        } catch (RemoteServiceException ex) {
            log.warn("Cashback credit for reward {} on account {} failed: {}",
                    grant.getRewardId(), grant.getAccountId(), ex.getMessage());
            grant.recordFailure(ex.getMessage());
        }
        return grants.save(grant);
    }

    private Map<String, String> campaignCodes(List<RewardGrant> items) {
        Map<String, String> codes = new HashMap<>();
        for (RewardGrant grant : items) {
            String campaignId = grant.getCampaignId();
            if (codes.containsKey(campaignId)) {
                continue;
            }
            campaigns.findById(campaignId).ifPresent(campaign -> codes.put(campaignId, campaign.getCode()));
        }
        return codes;
    }

    private static EvaluationResult.Skip skip(Campaign campaign, SkipReason reason) {
        return new EvaluationResult.Skip(campaign.getCode(), reason.name(), message(reason, campaign));
    }

    private static String message(SkipReason reason, Campaign campaign) {
        return switch (reason) {
            case ALREADY_EVALUATED -> "Campaign " + campaign.getCode() + " already evaluated this transaction.";
            case NOT_ENROLLED -> "Campaign " + campaign.getCode() + " requires enrollment first.";
            case PRODUCT_NOT_ELIGIBLE -> "Account product is not in " + campaign.productCodes() + ".";
            case OUTSIDE_WINDOW -> "Transaction date is outside " + campaign.getStartDate()
                    + " .. " + campaign.getEndDate() + ".";
            case AMOUNT_TOO_LOW -> "Transaction amount is below " + campaign.getMinTransactionAmount().toPlainString()
                    + " or cashback rounded to 0.";
            case CUSTOMER_CAP_REACHED -> "Customer already reached "
                    + campaign.getMaxCashbackPerCustomer().toPlainString() + " cashback.";
            case BUDGET_EXHAUSTED -> "Campaign budget " + campaign.getBudget().toPlainString() + " is used up.";
        };
    }

    private static String requireText(String value, String code, String message) {
        if (!StringUtils.hasText(value)) {
            throw new InvalidRequestException(code, message);
        }
        return value.trim();
    }

    private static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private static BigDecimal requireAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new InvalidRequestException("INVALID_AMOUNT", "Transaction amount must be greater than 0.");
        }
        if (amount.stripTrailingZeros().scale() > AMOUNT_SCALE) {
            throw new InvalidRequestException("INVALID_AMOUNT",
                    "Transaction amount must be a whole number of cents.");
        }
        return amount.setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
    }

    private static LocalDateTime requireOccurredAt(LocalDateTime occurredAt) {
        if (occurredAt == null) {
            throw new InvalidRequestException("INVALID_OCCURRED_AT", "Transaction occurredAt must be supplied.");
        }
        return occurredAt;
    }
}
