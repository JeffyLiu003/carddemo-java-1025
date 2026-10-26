package com.carddemo.workflow.infrastructure.persistence;

import com.carddemo.workflow.domain.model.WorkflowDefinition;
import com.carddemo.workflow.domain.repository.WorkflowDefinitionRepository;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WorkflowDefinitionJpaRepository
        extends JpaRepository<WorkflowDefinition, String>, WorkflowDefinitionRepository {

    @Override
    default List<WorkflowDefinition> findAllDefinitions() {
        return findAll(Sort.by(Sort.Order.asc("definitionCode")));
    }

    @Override
    default Optional<WorkflowDefinition> findByCode(String definitionCode) {
        return findById(definitionCode);
    }
}
