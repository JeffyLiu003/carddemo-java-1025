package com.carddemo.ledger.application;

import com.carddemo.ledger.domain.model.JournalLineDraft;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 记账命令。(sourceType, sourceId) 是幂等业务键；currency 仅用于请求契约完整性。
 */
public record PostJournalEntryCommand(
        String sourceType,
        String sourceId,
        String description,
        LocalDateTime postedAt,
        String currency,
        List<JournalLineDraft> lines) {
}
