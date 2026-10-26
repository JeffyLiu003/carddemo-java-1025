package com.carddemo.reporting.domain.repository;

import com.carddemo.reporting.domain.model.ReportDefinition;

import java.util.List;
import java.util.Optional;

public interface ReportDefinitionRepository {

    List<ReportDefinition> findAll();

    Optional<ReportDefinition> findById(String reportCode);
}
