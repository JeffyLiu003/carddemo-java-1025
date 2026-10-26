package com.carddemo.account.infrastructure.persistence;

import com.carddemo.account.domain.model.AccountHold;
import com.carddemo.account.domain.model.HoldStatus;
import com.carddemo.account.domain.repository.AccountHoldRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AccountHoldJpaRepository
        extends JpaRepository<AccountHold, String>, AccountHoldRepository {

    @Override
    Optional<AccountHold> findByHoldIdAndAccountId(String holdId, String accountId);

    @Override
    Optional<AccountHold> findByAccountIdAndReferenceId(String accountId, String referenceId);

    @Override
    List<AccountHold> findByAccountIdOrderByCreatedAtDesc(String accountId);

    @Override
    List<AccountHold> findByAccountIdAndStatusOrderByCreatedAtDesc(String accountId, HoldStatus status);

    @Query("""
            select coalesce(sum(h.amount), 0)
            from AccountHold h
            where h.accountId = :accountId
              and h.status = :status
              and (h.expiresAt is null or h.expiresAt > :now)
            """)
    BigDecimal sumByStatusAndUnexpired(@Param("accountId") String accountId,
                                       @Param("status") HoldStatus status,
                                       @Param("now") LocalDateTime now);

    @Override
    default BigDecimal sumActiveAmount(String accountId, LocalDateTime now) {
        return sumByStatusAndUnexpired(accountId, HoldStatus.ACTIVE, now);
    }
}
