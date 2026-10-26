package com.carddemo.document.domain.repository;

import com.carddemo.document.domain.model.DocumentMetadata;

import java.util.List;

/**
 * 文档列表分页结果：{"items": [...], "page": 0, "size": 10, "total": 2}。
 */
public record DocumentPage(List<DocumentMetadata> items, int page, int size, long total) {
}
