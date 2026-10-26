package com.carddemo.aml.domain.repository;

import com.carddemo.aml.domain.model.SanctionsEntry;

import java.util.List;

/**
 * 名单条目仓储端口（sanctions_entry）。
 */
public interface SanctionsEntryRepository {

    /**
     * 参与筛查的名单，按 entry_id 升序以保证命中顺序稳定。
     */
    List<SanctionsEntry> findActiveEntries();
}
