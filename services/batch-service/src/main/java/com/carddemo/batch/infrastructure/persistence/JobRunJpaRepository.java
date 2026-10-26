package com.carddemo.batch.infrastructure.persistence;

import com.carddemo.batch.domain.model.JobRun;
import com.carddemo.batch.domain.repository.JobRunFilter;
import com.carddemo.batch.domain.repository.JobRunRepository;
import com.carddemo.batch.domain.repository.PageResult;
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

public interface JobRunJpaRepository
        extends JpaRepository<JobRun, String>, JpaSpecificationExecutor<JobRun>, JobRunRepository {

    @Override
    default PageResult<JobRun> search(JobRunFilter filter, int page, int size) {
        Sort sort = Sort.by(Sort.Order.desc("startedAt"), Sort.Order.desc("runId"));
        Page<JobRun> result = findAll(specification(filter), PageRequest.of(page, size, sort));
        return new PageResult<>(result.getContent(), result.getTotalElements());
    }

    private static Specification<JobRun> specification(JobRunFilter filter) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (filter != null && StringUtils.hasText(filter.jobCode())) {
                predicates.add(builder.equal(root.get("jobCode"), filter.jobCode()));
            }
            if (filter != null && filter.status() != null) {
                predicates.add(builder.equal(root.get("status"), filter.status()));
            }
            return builder.and(predicates.toArray(new Predicate[0]));
        };
    }
}
