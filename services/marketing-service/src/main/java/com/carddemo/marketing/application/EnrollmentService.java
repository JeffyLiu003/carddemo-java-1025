package com.carddemo.marketing.application;

import com.carddemo.marketing.domain.exception.BusinessRuleException;
import com.carddemo.marketing.domain.exception.InvalidRequestException;
import com.carddemo.marketing.domain.exception.NotFoundException;
import com.carddemo.marketing.domain.model.AccountPosition;
import com.carddemo.marketing.domain.model.Campaign;
import com.carddemo.marketing.domain.model.CampaignStatus;
import com.carddemo.marketing.domain.model.Enrollment;
import com.carddemo.marketing.domain.repository.CampaignRepository;
import com.carddemo.marketing.domain.repository.EnrollmentRepository;
import com.carddemo.marketing.domain.repository.PageResult;
import com.carddemo.marketing.infrastructure.client.AccountClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 报名用例（设计 §4）：报名幂等、活动必须 ACTIVE、产品等级匹配、账户可用；资格查询给客户看。
 */
@Service
@Transactional
public class EnrollmentService {

    private static final int DEFAULT_PAGE_SIZE = 20;

    private final CampaignRepository campaigns;
    private final EnrollmentRepository enrollments;
    private final AccountClient accounts;

    public EnrollmentService(CampaignRepository campaigns, EnrollmentRepository enrollments,
                             AccountClient accounts) {
        this.campaigns = campaigns;
        this.enrollments = enrollments;
        this.accounts = accounts;
    }

    /**
     * 报名：重复报名返回已有记录（created=false，接口回 200），不重复调用 account-service。
     *
     * <p>首次报名校验活动 ACTIVE（409）、账户存在（404）、账户可用（409）、产品等级匹配（409）。
     */
    public EnrollmentOutcome enroll(String campaignId, String accountId, String cardNumber, String customerId) {
        Campaign campaign = requireCampaign(campaignId);
        String account = requireText(accountId, "INVALID_ACCOUNT_ID", "Account id must be supplied.");
        String customer = requireText(customerId, "INVALID_CUSTOMER_ID", "Customer id must be supplied.");

        Optional<Enrollment> existing =
                enrollments.findByCampaignIdAndAccountId(campaign.getCampaignId(), account);
        if (existing.isPresent()) {
            return new EnrollmentOutcome(existing.get(), false);
        }
        if (campaign.getStatus() != CampaignStatus.ACTIVE) {
            throw new BusinessRuleException("CAMPAIGN_NOT_ACTIVE",
                    "Campaign " + campaign.getCode() + " is " + campaign.getStatus() + " and cannot be enrolled.");
        }

        AccountPosition position = accounts.creditPosition(account);
        if (!position.activeStatus()) {
            throw new BusinessRuleException("ACCOUNT_INACTIVE", "Account " + account + " is not active.");
        }
        if (!campaign.isProductEligible(position.productCode())) {
            throw new BusinessRuleException("PRODUCT_NOT_ELIGIBLE",
                    "Account " + account + " product " + position.productCode()
                            + " is not eligible for campaign " + campaign.getCode() + ".");
        }

        Enrollment enrollment = Enrollment.enroll(UUID.randomUUID().toString(), campaign.getCampaignId(), account,
                cardNumber, customer, LocalDateTime.now());
        return new EnrollmentOutcome(enrollments.save(enrollment), true);
    }

    @Transactional(readOnly = true)
    public PageResult<Enrollment> list(String campaignId, int page, int size) {
        Campaign campaign = requireCampaign(campaignId);
        int safeSize = size <= 0 ? DEFAULT_PAGE_SIZE : size;
        return enrollments.search(campaign.getCampaignId(), Math.max(page, 0), safeSize);
    }

    /**
     * 客户可见活动与资格：返回全部 ACTIVE 活动；eligible 由账户产品+账户状态决定，
     * enrolled 按传入的 accountId / customerId / cardNumber 任一维度判定。
     */
    @Transactional(readOnly = true)
    public List<EligibilityView> eligibilities(String accountId, String cardNumber, String customerId) {
        AccountPosition position = StringUtils.hasText(accountId)
                ? accounts.creditPosition(accountId.trim())
                : null;
        return campaigns.findByStatus(CampaignStatus.ACTIVE).stream()
                .sorted(Comparator.comparing(Campaign::getStartDate).reversed()
                        .thenComparing(Campaign::getCode))
                .map(campaign -> EligibilityView.of(campaign,
                        isEligible(campaign, position),
                        isEnrolled(campaign, accountId, cardNumber, customerId)))
                .toList();
    }

    private static boolean isEligible(Campaign campaign, AccountPosition position) {
        return position != null && position.activeStatus() && campaign.isProductEligible(position.productCode());
    }

    private boolean isEnrolled(Campaign campaign, String accountId, String cardNumber, String customerId) {
        if (StringUtils.hasText(accountId)
                && enrollments.existsByCampaignIdAndAccountId(campaign.getCampaignId(), accountId.trim())) {
            return true;
        }
        if (StringUtils.hasText(customerId)
                && enrollments.existsByCampaignIdAndCustomerId(campaign.getCampaignId(), customerId.trim())) {
            return true;
        }
        return StringUtils.hasText(cardNumber)
                && enrollments.existsByCampaignIdAndCardNumber(campaign.getCampaignId(), cardNumber.trim());
    }

    private Campaign requireCampaign(String campaignId) {
        if (!StringUtils.hasText(campaignId)) {
            throw new NotFoundException("CAMPAIGN_NOT_FOUND", "Campaign " + campaignId + " not found");
        }
        return campaigns.findById(campaignId.trim())
                .orElseThrow(() -> new NotFoundException("CAMPAIGN_NOT_FOUND",
                        "Campaign " + campaignId + " not found"));
    }

    private static String requireText(String value, String code, String message) {
        if (!StringUtils.hasText(value)) {
            throw new InvalidRequestException(code, message);
        }
        return value.trim();
    }
}
