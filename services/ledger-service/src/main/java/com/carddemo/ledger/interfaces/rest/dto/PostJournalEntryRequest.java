package com.carddemo.ledger.interfaces.rest.dto;

import com.carddemo.ledger.application.PostJournalEntryCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

public record PostJournalEntryRequest(
        @NotBlank
        @Size(max = 20)
        String sourceType,

        @NotBlank
        @Size(max = 64)
        String sourceId,

        @Size(max = 255)
        String description,

        @NotNull
        LocalDateTime postedAt,

        @Size(max = 3)
        String currency,

        @NotEmpty
        @Valid
        List<JournalLineRequest> lines) {

    public PostJournalEntryCommand toCommand() {
        return new PostJournalEntryCommand(sourceType, sourceId, description, postedAt, currency,
                lines.stream().map(JournalLineRequest::toDraft).toList());
    }
}
