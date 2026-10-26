package com.carddemo.aml.infrastructure.persistence;

import com.carddemo.aml.domain.model.SanctionsEntry;
import com.carddemo.aml.domain.repository.SanctionsEntryRepository;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SanctionsEntryJpaRepository
        extends JpaRepository<SanctionsEntry, String>, SanctionsEntryRepository {

    @Override
    default List<SanctionsEntry> findActiveEntries() {
        return findAllByActiveTrueOrderByEntryIdAsc();
    }

    List<SanctionsEntry> findAllByActiveTrueOrderByEntryIdAsc();
}
