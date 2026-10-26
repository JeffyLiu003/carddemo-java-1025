package com.carddemo.fx.domain.repository;

import com.carddemo.fx.domain.model.FxRate;

import java.util.Optional;

/**
 * 汇率仓储端口。
 */
public interface FxRateRepository {

    FxRate save(FxRate rate);

    Optional<FxRate> findByPair(String baseCurrency, String quoteCurrency);

    PageResult<FxRate> search(int page, int size);
}
