package com.carddemo.collections.domain.repository;

import com.carddemo.collections.domain.model.DelinquencyCase;

import java.util.Optional;

public interface DelinquencyCaseRepository {

    DelinquencyCase save(DelinquencyCase delinquencyCase);

    Optional<DelinquencyCase> findById(String caseId);

    PageResult<DelinquencyCase> search(DelinquencyCaseFilter filter, int page, int size);
}
