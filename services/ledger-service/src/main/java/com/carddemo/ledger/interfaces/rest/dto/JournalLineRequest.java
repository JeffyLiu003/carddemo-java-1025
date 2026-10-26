package com.carddemo.ledger.interfaces.rest.dto;

import com.carddemo.ledger.domain.model.Direction;
import com.carddemo.ledger.domain.model.JournalLineDraft;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record JournalLineRequest(
        @NotBlank
        @Size(max = 20)
        String accountCode,

        @NotNull
        Direction direction,

        @NotNull
        @DecimalMin("0.01")
        @Digits(integer = 12, fraction = 2)
        BigDecimal amount) {

    public JournalLineDraft toDraft() {
        return new JournalLineDraft(accountCode, direction, amount);
    }
}
