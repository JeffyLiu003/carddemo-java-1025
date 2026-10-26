package com.carddemo.reference.infrastructure.persistence;

import com.carddemo.reference.domain.exception.BusinessRuleException;
import com.carddemo.reference.domain.model.ReferenceItem;
import com.carddemo.reference.domain.repository.ReferenceItemRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReferenceItemJpaRepository extends JpaRepository<ReferenceItem, String>,
        ReferenceItemRepository {

    List<ReferenceItem> findByDomainOrderBySortOrderAscCodeAsc(String domain);

    List<ReferenceItem> findByDomainAndActiveOrderBySortOrderAscCodeAsc(String domain, boolean active);

    @Override
    default Optional<ReferenceItem> findItem(String itemId) {
        return findById(itemId);
    }

    @Override
    default List<ReferenceItem> findItems(String domain, Boolean active) {
        return active == null
                ? findByDomainOrderBySortOrderAscCodeAsc(domain)
                : findByDomainAndActiveOrderBySortOrderAscCodeAsc(domain, active);
    }

    /**
     * 唯一约束 (domain, code) 兜底并发下的重复新增。
     */
    @Override
    default ReferenceItem saveItem(ReferenceItem item) {
        try {
            return saveAndFlush(item);
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessRuleException("REFERENCE_ITEM_EXISTS",
                    "Reference item " + item.getDomain() + "/" + item.getCode() + " already exists.");
        }
    }

    @Override
    default void deleteItem(ReferenceItem item) {
        delete(item);
    }
}
