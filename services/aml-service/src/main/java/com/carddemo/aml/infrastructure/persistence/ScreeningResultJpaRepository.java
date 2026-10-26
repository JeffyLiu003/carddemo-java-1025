package com.carddemo.aml.infrastructure.persistence;

import com.carddemo.aml.domain.model.ScreeningResult;
import com.carddemo.aml.domain.repository.ScreeningResultRepository;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ScreeningResultJpaRepository
        extends JpaRepository<ScreeningResult, String>, ScreeningResultRepository {

    @Override
    default ScreeningResult saveScreening(ScreeningResult screening) {
        return save(screening);
    }

    @Override
    default Optional<ScreeningResult> findScreening(String screeningId) {
        return findById(screeningId);
    }
}
