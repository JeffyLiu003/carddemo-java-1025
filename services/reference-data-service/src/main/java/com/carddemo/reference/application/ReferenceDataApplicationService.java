package com.carddemo.reference.application;

import com.carddemo.reference.domain.exception.BusinessRuleException;
import com.carddemo.reference.domain.exception.NotFoundException;
import com.carddemo.reference.domain.model.ReferenceItem;
import com.carddemo.reference.domain.model.ReferenceItemDraft;
import com.carddemo.reference.domain.model.ReferenceItemUpdate;
import com.carddemo.reference.domain.repository.ReferenceItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * 参考数据用例：按域查询、新增（(domain, code) 唯一，重复 409）、
 * 修改 label/sortOrder/active、物理删除（RD-01..04）。
 */
@Service
@Transactional
public class ReferenceDataApplicationService {

    private final ReferenceItemRepository items;

    public ReferenceDataApplicationService(ReferenceItemRepository items) {
        this.items = items;
    }

    @Transactional(readOnly = true)
    public List<ReferenceItem> list(String domain, Boolean active) {
        return items.findItems(ReferenceItem.normalizeKey(domain, "domain"), active);
    }

    @Transactional(readOnly = true)
    public ReferenceItem get(String domain, String code) {
        String normalizedDomain = ReferenceItem.normalizeKey(domain, "domain");
        String normalizedCode = ReferenceItem.normalizeKey(code, "code");
        return items.findByDomainAndCode(normalizedDomain, normalizedCode)
                .orElseThrow(() -> new NotFoundException("REFERENCE_ITEM_NOT_FOUND",
                        "Reference item " + normalizedDomain + "/" + normalizedCode + " not found"));
    }

    public ReferenceItem create(ReferenceItemDraft draft) {
        ReferenceItem item = new ReferenceItem(UUID.randomUUID().toString(), draft);
        if (items.existsByDomainAndCode(item.getDomain(), item.getCode())) {
            throw new BusinessRuleException("REFERENCE_ITEM_EXISTS",
                    "Reference item " + item.getDomain() + "/" + item.getCode() + " already exists.");
        }
        return items.saveItem(item);
    }

    public ReferenceItem update(String itemId, ReferenceItemUpdate update) {
        ReferenceItem item = getById(itemId);
        item.applyUpdate(update);
        return items.saveItem(item);
    }

    public void delete(String itemId) {
        items.deleteItem(getById(itemId));
    }

    @Transactional(readOnly = true)
    public ReferenceItem getById(String itemId) {
        return items.findItem(itemId)
                .orElseThrow(() -> new NotFoundException("REFERENCE_ITEM_NOT_FOUND",
                        "Reference item " + itemId + " not found"));
    }
}
