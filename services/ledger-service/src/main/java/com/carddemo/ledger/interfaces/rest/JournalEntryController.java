package com.carddemo.ledger.interfaces.rest;

import com.carddemo.ledger.application.JournalEntryApplicationService;
import com.carddemo.ledger.interfaces.rest.dto.JournalEntryListResponse;
import com.carddemo.ledger.interfaces.rest.dto.JournalEntryResponse;
import com.carddemo.ledger.interfaces.rest.dto.PostJournalEntryRequest;
import com.carddemo.ledger.interfaces.rest.dto.ReversalRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "日记账", description = "复式记账、冲正与按 (sourceType, sourceId) 幂等")
@RestController
@RequestMapping("/api/v1/journal-entries")
public class JournalEntryController {

    private final JournalEntryApplicationService entries;

    public JournalEntryController(JournalEntryApplicationService entries) {
        this.entries = entries;
    }

    @GetMapping
    public JournalEntryListResponse list(@RequestParam(required = false) String sourceId,
                                         @RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "20") int size) {
        int safePage = Math.max(page, 0);
        int safeSize = size <= 0 ? 20 : size;
        return JournalEntryListResponse.from(entries.list(sourceId, safePage, safeSize), safePage, safeSize);
    }

    @GetMapping("/{entryId}")
    public JournalEntryResponse get(@PathVariable String entryId) {
        return JournalEntryResponse.from(entries.get(entryId));
    }

    @Operation(summary = "记日记账",
            description = "至少两行、金额大于零、借贷合计相等；科目须存在且启用；posted_at 所在期间须存在且未关账。"
                    + "同一 (sourceType, sourceId) 内容一致返回原分录，内容不一致 409 JOURNAL_SOURCE_CONFLICT。")
    @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @Content(examples = @ExampleObject(
            name = "信用卡消费（DR 1100 / CR 2100）",
            value = """
                    {"sourceType":"POSTING_BATCH","sourceId":"b-1","description":"Card purchase",
                    "postedAt":"2026-10-26T04:00:00","currency":"USD",
                    "lines":[{"accountCode":"1100","direction":"DR","amount":25.00},
                             {"accountCode":"2100","direction":"CR","amount":25.00}]}
                    """)))
    @PostMapping
    public ResponseEntity<JournalEntryResponse> post(@Valid @RequestBody PostJournalEntryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(JournalEntryResponse.from(entries.post(request.toCommand())));
    }

    @Operation(summary = "冲正", description = "生成 sourceType=REVERSAL、sourceId=原分录号的反向分录；重复冲正返回已有冲正分录。")
    @PostMapping("/{entryId}/reversal")
    public ResponseEntity<JournalEntryResponse> reverse(@PathVariable String entryId,
                                                        @RequestBody(required = false) ReversalRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(JournalEntryResponse.from(entries.reverse(entryId, request == null ? null : request.postedAt())));
    }
}
