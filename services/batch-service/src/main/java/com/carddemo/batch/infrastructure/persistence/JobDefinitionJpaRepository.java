package com.carddemo.batch.infrastructure.persistence;

import com.carddemo.batch.domain.model.JobDefinition;
import com.carddemo.batch.domain.repository.JobDefinitionRepository;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface JobDefinitionJpaRepository
        extends JpaRepository<JobDefinition, String>, JobDefinitionRepository {

    @Override
    default List<JobDefinition> findAll() {
        return findAll(Sort.by(Sort.Order.asc("jobCode")));
    }

    @Override
    default Optional<JobDefinition> findByJobCode(String jobCode) {
        return findById(jobCode);
    }
}
