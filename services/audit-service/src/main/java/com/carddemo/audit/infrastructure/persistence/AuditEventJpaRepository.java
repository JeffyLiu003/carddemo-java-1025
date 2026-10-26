package com.carddemo.audit.infrastructure.persistence;

import com.carddemo.audit.domain.model.AuditEvent;
import com.carddemo.audit.domain.repository.AuditEventRepository;
import com.carddemo.audit.domain.repository.PageResult;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.util.StringUtils;

import java.util.Optional;

public interface AuditEventJpaRepository extends JpaRepository<AuditEvent, String>,
        JpaSpecificationExecutor<AuditEvent>, AuditEventRepository {

    @Override
    default AuditEvent append(AuditEvent event) {
        return save(event);
    }

    @Override
    default Optional<AuditEvent> findEvent(String eventId) {
        return findById(eventId);
    }

    /**
     * actor / resourceType / resourceId 任意组合精确匹配，newest first。
     */
    @Override
    default PageResult<AuditEvent> search(String actor, String resourceType, String resourceId,
                                          int page, int size) {
        Pageable pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "eventId")));
        Specification<AuditEvent> spec = (root, query, cb) -> cb.conjunction();
        if (StringUtils.hasText(actor)) {
            String value = actor.trim();
            spec = spec.and((root, query, cb) -> cb.equal(root.get("actor"), value));
        }
        if (StringUtils.hasText(resourceType)) {
            String value = resourceType.trim();
            spec = spec.and((root, query, cb) -> cb.equal(root.get("resourceType"), value));
        }
        if (StringUtils.hasText(resourceId)) {
            String value = resourceId.trim();
            spec = spec.and((root, query, cb) -> cb.equal(root.get("resourceId"), value));
        }
        Page<AuditEvent> result = findAll(spec, pageable);
        return new PageResult<>(result.getContent(), pageable.getPageNumber(), pageable.getPageSize(),
                result.getTotalElements());
    }
}
