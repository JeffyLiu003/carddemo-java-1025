package com.carddemo.reference.domain.model;

/**
 * 新增参考数据项的输入。domain/code 会规范化为大写；sortOrder 默认 0，active 默认 true。
 */
public record ReferenceItemDraft(String domain, String code, String label,
                                 Integer sortOrder, Boolean active) {
}
