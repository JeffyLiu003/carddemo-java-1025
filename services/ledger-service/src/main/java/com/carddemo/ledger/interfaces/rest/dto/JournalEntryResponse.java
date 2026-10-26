package com.carddemo.ledger.interfaces.rest.dto;

import com.carddemo.ledger.domain.model.JournalEntry;

import java.time.LocalDateTime;
import java.util.List;

public record JournalEntryResponse(
        String entryId,
        String sourceType,
        String sourceId,
        String description,
        LocalDateTime postedAt,
        String status,
        String reversalOf,
        LocalDateTime createdAt,
        long version,
        List<JournalLineResponse> lines) {

    public static JournalEntryResponse from(JournalEntry entry) {
        return new JournalEntryResponse(
                entry.getEntryId(),
                entry.getSourceType(),
                entry.getSourceId(),
                entry.getDescription(),
                entry.getPostedAt(),
                entry.getStatus().name(),
                entry.getReversalOf(),
                entry.getCreatedAt(),
                entry.getVersion(),
                entry.getLines().stream().map(JournalLineResponse::from).toList());
    }
}
