package com.carddemo.transaction.interfaces.rest.dto;

import com.carddemo.transaction.domain.model.PostingLeg;

import java.math.BigDecimal;

public record PostingLegResponse(
        int legNo,
        String accountRole,
        String direction,
        String ledgerAccountCode,
        BigDecimal amount) {

    public static PostingLegResponse from(PostingLeg leg) {
        return new PostingLegResponse(leg.getLegNo(), leg.getAccountRole(), leg.getDirection().name(),
                leg.getLedgerAccountCode(), leg.getAmount());
    }
}
