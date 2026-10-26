package com.carddemo.transaction.interfaces.rest.dto;

import com.carddemo.transaction.application.PostingBatchView;
import com.carddemo.transaction.domain.repository.PageResult;

import java.util.List;

public record PostingBatchListResponse(List<PostingBatchResponse> items, int page, int size, long total) {

    public static PostingBatchListResponse from(PageResult<PostingBatchView> result, int page, int size) {
        return new PostingBatchListResponse(
                result.items().stream().map(PostingBatchResponse::from).toList(),
                page,
                size,
                result.total());
    }
}
