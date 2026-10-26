package com.carddemo.batch.domain.repository;

import com.carddemo.batch.domain.model.JobDefinition;

import java.util.List;
import java.util.Optional;

public interface JobDefinitionRepository {

    List<JobDefinition> findAll();

    Optional<JobDefinition> findByJobCode(String jobCode);
}
