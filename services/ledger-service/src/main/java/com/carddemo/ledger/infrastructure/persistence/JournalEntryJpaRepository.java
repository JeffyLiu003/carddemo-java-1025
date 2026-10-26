package com.carddemo.ledger.infrastructure.persistence;

import com.carddemo.ledger.domain.exception.BusinessRuleException;
import com.carddemo.ledger.domain.model.JournalEntry;
import com.carddemo.ledger.domain.repository.AccountActivity;
import com.carddemo.ledger.domain.repository.JournalEntryRepository;
import com.carddemo.ledger.domain.repository.PageResult;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface JournalEntryJpaRepository
        extends JpaRepository<JournalEntry, String>, JournalEntryRepository {

    @Override
    @EntityGraph(attributePaths = "lines")
    Optional<JournalEntry> findByEntryId(String entryId);

    @EntityGraph(attributePaths = "lines")
    @Query("select e from JournalEntry e where e.sourceType = :sourceType and e.sourceId = :sourceId")
    Optional<JournalEntry> findWithLinesBySource(@Param("sourceType") String sourceType,
                                                 @Param("sourceId") String sourceId);

    @Override
    default Optional<JournalEntry> findBySourceTypeAndSourceId(String sourceType, String sourceId) {
        return findWithLinesBySource(sourceType, sourceId);
    }

    /**
     * 唯一索引 (source_type, source_id) 兜底并发下同一幂等键的重复写入。
     */
    @Override
    default JournalEntry save(JournalEntry entry) {
        try {
            return saveAndFlush(entry);
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessRuleException("JOURNAL_SOURCE_CONFLICT",
                    "Journal source " + entry.getSourceType() + "/" + entry.getSourceId()
                            + " already recorded with different content.");
        }
    }

    Page<JournalEntry> findBySourceId(String sourceId, Pageable pageable);

    @Override
    default PageResult<JournalEntry> search(String sourceId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")
                .and(Sort.by(Sort.Direction.DESC, "entryId")));
        Page<JournalEntry> result = StringUtils.hasText(sourceId)
                ? findBySourceId(sourceId, pageable)
                : findAll(pageable);
        // 事务内显式初始化，避免 open-in-view=false 时在控制器映射阶段触发懒加载异常
        result.getContent().forEach(entry -> entry.getLines().size());
        return new PageResult<>(result.getContent(), result.getTotalElements());
    }

    @Override
    @Query("""
            select new com.carddemo.ledger.domain.repository.AccountActivity(
                l.accountCode, l.direction, sum(l.amount))
            from JournalLine l
            where l.entry.postedAt >= :from and l.entry.postedAt < :to
            group by l.accountCode, l.direction
            """)
    List<AccountActivity> aggregateByAccount(@Param("from") LocalDateTime from,
                                             @Param("to") LocalDateTime to);
}
