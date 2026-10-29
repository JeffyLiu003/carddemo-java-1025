package com.carddemo.marketing.application;

import com.carddemo.marketing.domain.exception.BusinessRuleException;
import com.carddemo.marketing.domain.exception.InvalidRequestException;
import com.carddemo.marketing.domain.model.Campaign;
import com.carddemo.marketing.domain.model.ReversalCalculator;
import com.carddemo.marketing.domain.model.ReversalDecision;
import com.carddemo.marketing.domain.model.ReversalType;
import com.carddemo.marketing.domain.model.RewardGrant;
import com.carddemo.marketing.domain.model.RewardReversal;
import com.carddemo.marketing.domain.repository.CampaignRepository;
import com.carddemo.marketing.domain.repository.RewardGrantRepository;
import com.carddemo.marketing.domain.repository.RewardReversalRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * 退款与冲正受理（设计 §4、§5.1，PRD REQ-CB-07 / BR-CB-12）。
 *
 * <p>事务边界：故意不加类级 {@code @Transactional}——口径未确认时记录需要保留，随后再抛 409，
 * 与 {@link RewardService} 的“记录先落库、结论后返回”一致。本次只计算追扣金额，不移动资金。
 */
@Service
public class RewardReversalService {

    private static final Logger log = LoggerFactory.getLogger(RewardReversalService.class);
    private static final int AMOUNT_SCALE = 2;

    private final RewardGrantRepository grants;
    private final CampaignRepository campaigns;
    private final RewardReversalRepository reversals;

    public RewardReversalService(RewardGrantRepository grants, CampaignRepository campaigns,
                                 RewardReversalRepository reversals) {
        this.grants = grants;
        this.campaigns = campaigns;
        this.reversals = reversals;
    }

    /**
     * 受理一笔退款或冲正：逐条命中原消费的奖励记录并给出追扣结论；口径未确认时记录 PENDING 并返回 409。
     */
    public RewardReversalListView accept(ReverseRewardCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        String reversalTransactionId = requireText(command.reversalTransactionId(),
                "INVALID_REVERSAL_TRANSACTION_ID", "Reversal transaction id must be supplied.");
        String originalTransactionId = requireText(command.originalTransactionId(),
                "INVALID_TRANSACTION_ID", "Original transaction id must be supplied.");
        ReversalType type = Objects.requireNonNull(command.reversalType(), "reversalType must not be null");
        BigDecimal amount = requireAmount(command.reversalAmount());

        List<RewardGrant> matched = grants.findByTransactionId(originalTransactionId);
        if (matched.isEmpty()) {
            log.info("Reversal {} references original transaction {} without any reward grant",
                    reversalTransactionId, originalTransactionId);
            return new RewardReversalListView(List.of());
        }

        List<RewardReversalView> views = new ArrayList<>();
        String unconfirmedReason = null;
        for (RewardGrant grant : matched) {
            Campaign campaign = campaigns.findById(grant.getCampaignId()).orElse(null);
            String campaignCode = campaign == null ? null : campaign.getCode();
            Optional<RewardReversal> existing = reversals.findByReversalTransactionIdAndRewardId(
                    reversalTransactionId, grant.getRewardId());
            if (existing.isPresent()) {
                views.add(RewardReversalView.of(existing.get(), campaignCode));
                continue;
            }
            ReversalDecision decision = ReversalCalculator.decide(
                    campaign == null ? null : campaign.getReversalPolicy(), grant.getStatus(),
                    grant.getCashbackAmount(), grant.getTransactionAmount(), amount);
            RewardReversal saved = reversals.save(RewardReversal.accept(UUID.randomUUID().toString(),
                    grant.getCampaignId(), grant.getRewardId(), reversalTransactionId, type, amount,
                    decision, LocalDateTime.now()));
            views.add(RewardReversalView.of(saved, campaignCode));
            if (decision.isUnconfirmed()) {
                unconfirmedReason = decision.reason();
            }
        }
        if (unconfirmedReason != null) {
            throw new BusinessRuleException(unconfirmedReason, message(unconfirmedReason));
        }
        return new RewardReversalListView(views);
    }

    private static String message(String reason) {
        return switch (reason) {
            case ReversalCalculator.REVERSAL_POLICY_UNCONFIRMED ->
                    "Campaign reversal policy is not confirmed; the reversal was recorded as PENDING.";
            case ReversalCalculator.PARTIAL_REVERSAL_POLICY_UNCONFIRMED ->
                    "Partial reversal under FULL policy needs a confirmed rule; the reversal was recorded as PENDING.";
            default -> "Reversal needs business confirmation; the reversal was recorded as PENDING.";
        };
    }

    private static String requireText(String value, String code, String message) {
        if (!StringUtils.hasText(value)) {
            throw new InvalidRequestException(code, message);
        }
        return value.trim();
    }

    private static BigDecimal requireAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new InvalidRequestException("INVALID_AMOUNT", "Reversal amount must be greater than 0.");
        }
        return amount.setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
    }
}
