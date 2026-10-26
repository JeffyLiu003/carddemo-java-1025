package com.carddemo.marketing.application;

import com.carddemo.marketing.domain.exception.BusinessRuleException;
import com.carddemo.marketing.domain.exception.NotFoundException;
import com.carddemo.marketing.domain.model.Campaign;
import com.carddemo.marketing.domain.model.CampaignStatus;
import com.carddemo.marketing.domain.model.RewardGrantStatus;
import com.carddemo.marketing.domain.repository.CampaignRepository;
import com.carddemo.marketing.domain.repository.EnrollmentRepository;
import com.carddemo.marketing.domain.repository.PageResult;
import com.carddemo.marketing.domain.repository.RewardGrantRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * 活动用例：创建、列表、详情、发布/暂停/结束、效果与看板汇总（设计 §4）。
 */
@Service
@Transactional
public class CampaignService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int RATE_SCALE = 4;
    private static final BigDecimal ZERO = new BigDecimal("0.00");

    private final CampaignRepository campaigns;
    private final EnrollmentRepository enrollments;
    private final RewardGrantRepository grants;

    public CampaignService(CampaignRepository campaigns, EnrollmentRepository enrollments,
                           RewardGrantRepository grants) {
        this.campaigns = campaigns;
        this.enrollments = enrollments;
        this.grants = grants;
    }

    /**
     * 创建活动：一律 DRAFT；同 code 已存在时 409，非法期限/费率/上限/预算由领域校验映射为 400。
     */
    public Campaign create(CreateCampaignCommand command) {
        Optional<Campaign> existing = StringUtils.hasText(command.code())
                ? campaigns.findByCode(normalizeCode(command.code()))
                : Optional.empty();
        if (existing.isPresent()) {
            throw new BusinessRuleException("CAMPAIGN_CODE_ALREADY_EXISTS",
                    "Campaign code " + normalizeCode(command.code()) + " already exists.");
        }
        return campaigns.save(Campaign.create(UUID.randomUUID().toString(), command.toTerms(), LocalDateTime.now()));
    }

    @Transactional(readOnly = true)
    public Campaign get(String campaignId) {
        return require(campaignId);
    }

    @Transactional(readOnly = true)
    public PageResult<Campaign> list(String status, int page, int size) {
        int safeSize = size <= 0 ? DEFAULT_PAGE_SIZE : size;
        return campaigns.search(CampaignStatus.parse(status), Math.max(page, 0), safeSize);
    }

    public Campaign activate(String campaignId) {
        Campaign campaign = require(campaignId);
        campaign.activate(LocalDateTime.now());
        return campaigns.save(campaign);
    }

    public Campaign pause(String campaignId) {
        Campaign campaign = require(campaignId);
        campaign.pause(LocalDateTime.now());
        return campaigns.save(campaign);
    }

    public Campaign end(String campaignId) {
        Campaign campaign = require(campaignId);
        campaign.end(LocalDateTime.now());
        return campaigns.save(campaign);
    }

    /**
     * 活动效果：报名数、返现笔数/已入账/待入账、已入账返现合计、预算使用率、报名→返现转化率。
     */
    @Transactional(readOnly = true)
    public CampaignPerformanceView performance(String campaignId) {
        Campaign campaign = require(campaignId);
        long enrolledCount = enrollments.countByCampaignId(campaignId);
        long rewardCount = grants.countByCampaignId(campaignId);
        long grantedCount = grants.countByCampaignIdAndStatus(campaignId, RewardGrantStatus.GRANTED);
        long pendingCount = grants.countByCampaignIdAndStatus(campaignId, RewardGrantStatus.PENDING);
        BigDecimal cashbackTotal = grants.grantedCashback(campaignId);
        return new CampaignPerformanceView(
                campaign.getCampaignId(),
                campaign.getCode(),
                campaign.getStatus().name(),
                campaign.getStartDate(),
                campaign.getEndDate(),
                enrolledCount,
                rewardCount,
                grantedCount,
                pendingCount,
                cashbackTotal,
                campaign.getBudget(),
                campaign.getConsumedBudget(),
                campaign.budgetRemaining(),
                rate(campaign.getConsumedBudget(), campaign.getBudget()),
                rate(BigDecimal.valueOf(rewardCount), BigDecimal.valueOf(enrolledCount)));
    }

    /**
     * 看板汇总：进行中活动数、已入账返现笔数与金额、待入账笔数。
     */
    @Transactional(readOnly = true)
    public MarketingSummaryView summary() {
        return new MarketingSummaryView(
                campaigns.countByStatus(CampaignStatus.ACTIVE),
                grants.countByStatus(RewardGrantStatus.GRANTED),
                grants.totalGrantedCashback(),
                grants.countByStatus(RewardGrantStatus.PENDING));
    }

    private Campaign require(String campaignId) {
        if (!StringUtils.hasText(campaignId)) {
            throw new NotFoundException("CAMPAIGN_NOT_FOUND", "Campaign " + campaignId + " not found");
        }
        return campaigns.findById(campaignId.trim())
                .orElseThrow(() -> new NotFoundException("CAMPAIGN_NOT_FOUND",
                        "Campaign " + campaignId + " not found"));
    }

    /**
     * 活动编码与 {@link Campaign} 一致地归一为去空格大写，避免大小写变体绕过唯一约束。
     */
    private static String normalizeCode(String code) {
        return code.trim().toUpperCase(Locale.ROOT);
    }

    private static BigDecimal rate(BigDecimal numerator, BigDecimal denominator) {
        if (denominator == null || denominator.signum() == 0) {
            return ZERO.setScale(RATE_SCALE, RoundingMode.HALF_UP);
        }
        return numerator.divide(denominator, RATE_SCALE, RoundingMode.HALF_UP);
    }
}
