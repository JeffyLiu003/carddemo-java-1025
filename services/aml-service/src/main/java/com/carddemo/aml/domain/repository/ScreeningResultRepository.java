package com.carddemo.aml.domain.repository;

import com.carddemo.aml.domain.model.ScreeningResult;

import java.util.Optional;

/**
 * 筛查结果仓储端口（screening_result）。
 */
public interface ScreeningResultRepository {

    ScreeningResult saveScreening(ScreeningResult screening);

    Optional<ScreeningResult> findScreening(String screeningId);
}
