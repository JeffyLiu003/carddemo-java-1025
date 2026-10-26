package com.carddemo.fx.infrastructure.persistence;

import com.carddemo.fx.domain.exception.BusinessRuleException;
import com.carddemo.fx.domain.model.FxConversion;
import com.carddemo.fx.domain.repository.FxConversionRepository;
import com.carddemo.fx.domain.repository.PageResult;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FxConversionJpaRepository
        extends JpaRepository<FxConversion, String>, FxConversionRepository {

    /**
     * 唯一索引 (quote_id) 兜底并发执行同一报价。
     */
    @Override
    default FxConversion save(FxConversion conversion) {
        try {
            return saveAndFlush(conversion);
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessRuleException("QUOTE_ALREADY_CONSUMED",
                    "Quote " + conversion.getQuoteId() + " has already been converted.");
        }
    }

    @Override
    default PageResult<FxConversion> search(int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), size,
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "conversionId")));
        Page<FxConversion> result = findAll(pageable);
        return new PageResult<>(result.getContent(), pageable.getPageNumber(), pageable.getPageSize(),
                result.getTotalElements());
    }
}
