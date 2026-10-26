package com.carddemo.reference.domain.model;

import com.carddemo.reference.domain.exception.InvalidRequestException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.util.Locale;

/**
 * 参考数据项聚合。同一 (domain, code) 全局唯一；domain/code 规范化大写且创建后不可变，
 * 可维护 label / sortOrder / active（见 docs/design/15-operations-support.md §7）。
 */
@Entity
@Table(name = "reference_item")
public class ReferenceItem {

    @Id
    @Column(name = "item_id", length = 36, nullable = false, updatable = false)
    private String itemId;

    @Column(name = "domain", length = 32, nullable = false, updatable = false)
    private String domain;

    @Column(name = "code", length = 32, nullable = false, updatable = false)
    private String code;

    @Column(name = "label", length = 120, nullable = false)
    private String label;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected ReferenceItem() {
        // for JPA
    }

    public ReferenceItem(String itemId, ReferenceItemDraft draft) {
        this.itemId = requireText(itemId, "itemId", 36);
        if (draft == null) {
            throw new InvalidRequestException("VALIDATION_FAILED", "Reference item body must be supplied.");
        }
        this.domain = normalizeKey(draft.domain(), "domain");
        this.code = normalizeKey(draft.code(), "code");
        this.label = requireLabel(draft.label());
        this.sortOrder = normalizeSortOrder(draft.sortOrder());
        this.active = draft.active() == null || draft.active();
    }

    public void applyUpdate(ReferenceItemUpdate update) {
        if (update == null) {
            throw new InvalidRequestException("VALIDATION_FAILED", "Reference item body must be supplied.");
        }
        this.label = requireLabel(update.label());
        this.sortOrder = normalizeSortOrder(update.sortOrder());
        this.active = update.active() == null || update.active();
    }

    /**
     * domain/code 规范化：去空白 + 大写。
     */
    public static String normalizeKey(String value, String field) {
        return requireText(value, field, 32).toUpperCase(Locale.ROOT);
    }

    private static String requireLabel(String value) {
        return requireText(value, "label", 120);
    }

    private static int normalizeSortOrder(Integer value) {
        if (value == null) {
            return 0;
        }
        if (value < 0) {
            throw new InvalidRequestException("VALIDATION_FAILED", "sortOrder must not be negative.");
        }
        return value;
    }

    private static String requireText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new InvalidRequestException("VALIDATION_FAILED", field + " must be supplied.");
        }
        String trimmed = value.trim();
        if (trimmed.length() > maxLength) {
            throw new InvalidRequestException("VALIDATION_FAILED",
                    field + " must be " + maxLength + " characters or less.");
        }
        return trimmed;
    }

    public String getItemId() {
        return itemId;
    }

    public String getDomain() {
        return domain;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public boolean isActive() {
        return active;
    }

    public long getVersion() {
        return version;
    }
}
