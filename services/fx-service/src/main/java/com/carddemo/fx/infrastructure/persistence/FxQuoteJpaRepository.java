package com.carddemo.fx.infrastructure.persistence;

import com.carddemo.fx.domain.model.FxQuote;
import com.carddemo.fx.domain.repository.FxQuoteRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface FxQuoteJpaRepository extends JpaRepository<FxQuote, String>, FxQuoteRepository {

    @Override
    Optional<FxQuote> findByQuoteId(String quoteId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select q from FxQuote q where q.quoteId = :quoteId")
    Optional<FxQuote> lockByQuoteIdQuery(@Param("quoteId") String quoteId);

    @Override
    default Optional<FxQuote> lockByQuoteId(String quoteId) {
        return lockByQuoteIdQuery(quoteId);
    }
}
