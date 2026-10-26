package com.carddemo.marketing.infrastructure.persistence;

import com.carddemo.marketing.domain.exception.BusinessRuleException;
import com.carddemo.marketing.domain.model.Enrollment;
import com.carddemo.marketing.domain.repository.EnrollmentRepository;
import com.carddemo.marketing.domain.repository.PageResult;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EnrollmentJpaRepository extends JpaRepository<Enrollment, String>, EnrollmentRepository {

    @Override
    Optional<Enrollment> findByCampaignIdAndAccountId(String campaignId, String accountId);

    Page<Enrollment> findByCampaignId(String campaignId, Pageable pageable);

    /**
     * 唯一索引 uk_enrollment_campaign_account 兜底并发重复报名。
     */
    @Override
    default Enrollment save(Enrollment enrollment) {
        try {
            return saveAndFlush(enrollment);
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessRuleException("ENROLLMENT_ALREADY_EXISTS",
                    "Account " + enrollment.getAccountId() + " already enrolled in campaign "
                            + enrollment.getCampaignId() + ".");
        }
    }

    @Override
    default PageResult<Enrollment> search(String campaignId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "enrolledAt")
                .and(Sort.by(Sort.Direction.DESC, "enrollmentId")));
        Page<Enrollment> result = findByCampaignId(campaignId, pageable);
        return new PageResult<>(result.getContent(), result.getTotalElements());
    }
}
