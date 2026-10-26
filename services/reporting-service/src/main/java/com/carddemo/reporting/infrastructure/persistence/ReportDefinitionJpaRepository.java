package com.carddemo.reporting.infrastructure.persistence;

import com.carddemo.reporting.domain.model.ReportDefinition;
import com.carddemo.reporting.domain.repository.ReportDefinitionRepository;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReportDefinitionJpaRepository
        extends JpaRepository<ReportDefinition, String>, ReportDefinitionRepository {

    @Override
    default List<ReportDefinition> findAll() {
        return findAll(Sort.by(Sort.Direction.ASC, "reportCode"));
    }
}
