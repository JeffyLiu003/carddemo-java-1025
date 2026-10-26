package com.carddemo.reconciliation.infrastructure.persistence;

import com.carddemo.reconciliation.domain.model.EodRun;
import com.carddemo.reconciliation.domain.repository.EodRunRepository;
import com.carddemo.reconciliation.domain.repository.PageResult;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EodRunJpaRepository extends JpaRepository<EodRun, String>, EodRunRepository {

    @Override
    default PageResult<EodRun> search(int page, int size) {
        Page<EodRun> result = findAll(PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "startedAt").and(Sort.by(Sort.Direction.DESC, "runId"))));
        return new PageResult<>(result.getContent(), result.getTotalElements());
    }
}
