package com.carddemo.workflow.domain.repository;

import com.carddemo.workflow.domain.model.WorkflowDefinition;

import java.util.List;
import java.util.Optional;

/**
 * 流程定义仓储端口。实现见 infrastructure.persistence.WorkflowDefinitionJpaRepository。
 */
public interface WorkflowDefinitionRepository {

    List<WorkflowDefinition> findAllDefinitions();

    Optional<WorkflowDefinition> findByCode(String definitionCode);
}
