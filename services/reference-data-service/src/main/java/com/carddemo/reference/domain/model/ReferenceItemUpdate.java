package com.carddemo.reference.domain.model;

/**
 * 修改参考数据项的输入：只允许改 label/sortOrder/active；domain/code 不可变。
 */
public record ReferenceItemUpdate(String label, Integer sortOrder, Boolean active) {
}
