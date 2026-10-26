package com.carddemo.collections.infrastructure.persistence;

import com.carddemo.collections.domain.model.DelinquencyCase;
import com.carddemo.collections.domain.repository.DelinquencyCaseFilter;
import com.carddemo.collections.domain.repository.DelinquencyCaseRepository;
import com.carddemo.collections.domain.repository.PageResult;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.ArrayList;
import java.util.List;

public interface DelinquencyCaseJpaRepository
        extends JpaRepository<DelinquencyCase, String>, JpaSpecificationExecutor<DelinquencyCase>,
        DelinquencyCaseRepository {

    @Override
    default PageResult<DelinquencyCase> search(DelinquencyCaseFilter filter, int page, int size) {
        Sort sort = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("caseId"));
        Page<DelinquencyCase> result = findAll(specification(filter), PageRequest.of(page, size, sort));
        return new PageResult<>(result.getContent(), result.getTotalElements());
    }

    private static Specification<DelinquencyCase> specification(DelinquencyCaseFilter filter) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (filter != null && filter.status() != null) {
                predicates.add(builder.equal(root.get("status"), filter.status()));
            }
            if (filter != null && filter.bucket() != null) {
                predicates.add(builder.equal(root.get("bucket"), filter.bucket()));
            }
            return builder.and(predicates.toArray(new Predicate[0]));
        };
    }
}
