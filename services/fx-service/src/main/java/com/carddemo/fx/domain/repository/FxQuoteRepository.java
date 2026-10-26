package com.carddemo.fx.domain.repository;

import com.carddemo.fx.domain.model.FxQuote;

import java.util.Optional;

/**
 * 报价仓储端口。
 */
public interface FxQuoteRepository {

    FxQuote save(FxQuote quote);

    Optional<FxQuote> findByQuoteId(String quoteId);

    /**
     * 悲观锁读取。执行换汇时串行化同一报价的并发消费。
     */
    Optional<FxQuote> lockByQuoteId(String quoteId);
}
