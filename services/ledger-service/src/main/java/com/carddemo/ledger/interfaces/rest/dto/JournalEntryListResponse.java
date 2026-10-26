package com.carddemo.ledger.interfaces.rest.dto;

import com.carddemo.ledger.domain.model.JournalEntry;
import com.carddemo.ledger.domain.repository.PageResult;

import java.util.List;

public record JournalEntryListResponse(
        List<JournalEntryResponse> items,
        int page,
        int size,
        long total) {

    public static JournalEntryListResponse from(PageResult<JournalEntry> result, int page, int size) {
        return new JournalEntryListResponse(
                result.items().stream().map(JournalEntryResponse::from).toList(),
                page,
                size,
                result.total());
    }
}
