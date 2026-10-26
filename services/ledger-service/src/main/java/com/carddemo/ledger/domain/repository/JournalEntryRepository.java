package com.carddemo.ledger.domain.repository;

import com.carddemo.ledger.domain.model.JournalEntry;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 日记账仓储端口。列表查询返回的分录已初始化 lines，供 open-in-view=false 的控制器直接映射。
 */
public interface JournalEntryRepository {

    JournalEntry save(JournalEntry entry);

    Optional<JournalEntry> findByEntryId(String entryId);

    Optional<JournalEntry> findBySourceTypeAndSourceId(String sourceType, String sourceId);

    PageResult<JournalEntry> search(String sourceId, int page, int size);

    /**
     * 试算平衡：按 posted_at 半开区间 [from, to) 汇总每个科目每个方向的金额。
     */
    List<AccountActivity> aggregateByAccount(LocalDateTime from, LocalDateTime to);
}
