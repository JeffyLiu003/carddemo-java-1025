package com.carddemo.loan.infrastructure.persistence;

import com.carddemo.loan.domain.model.LoanApplication;
import com.carddemo.loan.domain.model.LoanApplicationStatus;
import com.carddemo.loan.domain.repository.LoanApplicationRepository;
import com.carddemo.loan.domain.repository.PageResult;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public interface LoanApplicationJpaRepository
        extends JpaRepository<LoanApplication, String>, JpaSpecificationExecutor<LoanApplication>,
        LoanApplicationRepository {

    @Override
    default LoanApplication saveApplication(LoanApplication application) {
        return saveAndFlush(application);
    }

    @Override
    default Optional<LoanApplication> findByApplicationId(String applicationId) {
        return findById(applicationId);
    }

    @Override
    default PageResult<LoanApplication> search(LoanApplicationStatus status, int page, int size) {
        Sort sort = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("applicationId"));
        Page<LoanApplication> result = findAll(specification(status), PageRequest.of(page, size, sort));
        return new PageResult<>(result.getContent(), result.getTotalElements());
    }

    private static Specification<LoanApplication> specification(LoanApplicationStatus status) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null) {
                predicates.add(builder.equal(root.get("status"), status));
            }
            return builder.and(predicates.toArray(new Predicate[0]));
        };
    }
}
