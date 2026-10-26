package com.carddemo.marketing.infrastructure.persistence;

import com.carddemo.marketing.domain.exception.BusinessRuleException;
import com.carddemo.marketing.domain.model.Campaign;
import com.carddemo.marketing.domain.model.CampaignStatus;
import com.carddemo.marketing.domain.repository.CampaignRepository;
import com.carddemo.marketing.domain.repository.PageResult;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CampaignJpaRepository extends JpaRepository<Campaign, String>, CampaignRepository {

    @Override
    Optional<Campaign> findByCode(String code);

    @Override
    List<Campaign> findByStatus(CampaignStatus status);

    Page<Campaign> findByStatus(CampaignStatus status, Pageable pageable);

    /**
     * 唯一索引 uk_campaign_code 兜底并发下的同编码重复创建。
     */
    @Override
    default Campaign save(Campaign campaign) {
        try {
            return saveAndFlush(campaign);
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessRuleException("CAMPAIGN_CODE_ALREADY_EXISTS",
                    "Campaign code " + campaign.getCode() + " already exists.");
        }
    }

    @Override
    default PageResult<Campaign> search(CampaignStatus status, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")
                .and(Sort.by(Sort.Direction.DESC, "campaignId")));
        Page<Campaign> result = status == null ? findAll(pageable) : findByStatus(status, pageable);
        return new PageResult<>(result.getContent(), result.getTotalElements());
    }
}
