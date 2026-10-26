package com.carddemo.ledger.interfaces.rest.dto;

import com.carddemo.ledger.domain.model.JournalLine;

import java.math.BigDecimal;

public record JournalLineResponse(
        int lineNo,
        String accountCode,
        String direction,
        BigDecimal amount) {

    public static JournalLineResponse from(JournalLine line) {
        return new JournalLineResponse(line.getLineNo(), line.getAccountCode(),
                line.getDirection().name(), line.getAmount());
    }
}
