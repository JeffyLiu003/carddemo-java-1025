package com.carddemo.dispute.infrastructure.persistence;

import com.carddemo.dispute.domain.model.DisputeCase;
import com.carddemo.dispute.domain.repository.DisputeCaseFilter;
import com.carddemo.dispute.domain.repository.DisputeCaseRepository;
import com.carddemo.dispute.domain.repository.PageResult;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public interface DisputeCaseJpaRepository
        extends JpaRepository<DisputeCase, String>, JpaSpecificationExecutor<DisputeCase>,
        DisputeCaseRepository {

    @Override
    default DisputeCase saveCase(DisputeCase disputeCase) {
        return saveAndFlush(disputeCase);
    }

    @Override
    default Optional<DisputeCase> findByCaseId(String caseId) {
        return findById(caseId);
    }

    @Override
    default PageResult<DisputeCase> search(DisputeCaseFilter filter, int page, int size) {
        Sort sort = Sort.by(Sort.Order.desc("openedAt"), Sort.Order.desc("caseId"));
        Page<DisputeCase> result = findAll(specification(filter), PageRequest.of(page, size, sort));
        return new PageResult<>(result.getContent(), result.getTotalElements());
    }

    private static Specification<DisputeCase> specification(DisputeCaseFilter filter) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (filter != null && filter.status() != null) {
                predicates.add(builder.equal(root.get("status"), filter.status()));
            }
            if (filter != null && StringUtils.hasText(filter.accountId())) {
                predicates.add(builder.equal(root.get("accountId"), filter.accountId()));
            }
            return builder.and(predicates.toArray(new Predicate[0]));
        };
    }
}
