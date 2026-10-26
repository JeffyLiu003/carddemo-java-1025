package com.carddemo.reference.domain.repository;

import com.carddemo.reference.domain.model.ReferenceItem;

import java.util.List;
import java.util.Optional;

public interface ReferenceItemRepository {

    Optional<ReferenceItem> findItem(String itemId);

    Optional<ReferenceItem> findByDomainAndCode(String domain, String code);

    boolean existsByDomainAndCode(String domain, String code);

    /**
     * @param domain 已规范化的大写域
     * @param active null 表示不过滤
     */
    List<ReferenceItem> findItems(String domain, Boolean active);

    ReferenceItem saveItem(ReferenceItem item);

    void deleteItem(ReferenceItem item);
}
