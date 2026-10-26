package com.carddemo.fx.domain.repository;

import com.carddemo.fx.domain.model.FxConversion;

/**
 * 换汇记录仓储端口。
 */
public interface FxConversionRepository {

    FxConversion save(FxConversion conversion);

    PageResult<FxConversion> search(int page, int size);
}
