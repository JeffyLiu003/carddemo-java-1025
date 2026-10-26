package com.carddemo.reporting.infrastructure.persistence;

import com.carddemo.reporting.domain.model.ReportRun;
import com.carddemo.reporting.domain.repository.PageResult;
import com.carddemo.reporting.domain.repository.ReportRunRepository;
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

public interface ReportRunJpaRepository
        extends JpaRepository<ReportRun, String>, JpaSpecificationExecutor<ReportRun>, ReportRunRepository {

    @Override
    default PageResult<ReportRun> search(String reportCode, int page, int size) {
        Sort sort = Sort.by(Sort.Order.desc("generatedAt"), Sort.Order.desc("runId"));
        Page<ReportRun> result = findAll(specification(reportCode), PageRequest.of(page, size, sort));
        return new PageResult<>(result.getContent(), result.getTotalElements());
    }

    private static Specification<ReportRun> specification(String reportCode) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (StringUtils.hasText(reportCode)) {
                predicates.add(builder.equal(root.get("reportCode"), reportCode));
            }
            return builder.and(predicates.toArray(new Predicate[0]));
        };
    }
}
