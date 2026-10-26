package com.carddemo.fx.infrastructure.persistence;

import com.carddemo.fx.domain.exception.BusinessRuleException;
import com.carddemo.fx.domain.model.FxRate;
import com.carddemo.fx.domain.repository.FxRateRepository;
import com.carddemo.fx.domain.repository.PageResult;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FxRateJpaRepository extends JpaRepository<FxRate, String>, FxRateRepository {

    Optional<FxRate> findByBaseCurrencyAndQuoteCurrency(String baseCurrency, String quoteCurrency);

    @Override
    default Optional<FxRate> findByPair(String baseCurrency, String quoteCurrency) {
        return findByBaseCurrencyAndQuoteCurrency(baseCurrency, quoteCurrency);
    }

    /**
     * 唯一索引 (base_currency, quote_currency) 兜底并发 upsert。
     */
    @Override
    default FxRate save(FxRate rate) {
        try {
            return saveAndFlush(rate);
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessRuleException("RATE_CONFLICT",
                    "FX rate " + rate.getBaseCurrency() + "/" + rate.getQuoteCurrency()
                            + " was modified concurrently, please retry.");
        }
    }

    @Override
    default PageResult<FxRate> search(int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), size,
                Sort.by(Sort.Direction.ASC, "baseCurrency").and(Sort.by(Sort.Direction.ASC, "quoteCurrency")));
        Page<FxRate> result = findAll(pageable);
        return new PageResult<>(result.getContent(), pageable.getPageNumber(), pageable.getPageSize(),
                result.getTotalElements());
    }
}
