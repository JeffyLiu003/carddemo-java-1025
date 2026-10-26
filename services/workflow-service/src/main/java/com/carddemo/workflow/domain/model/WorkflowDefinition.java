package com.carddemo.workflow.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.util.Arrays;
import java.util.List;

/**
 * 流程定义（WORKFLOW_DEFINITION）。steps 以逗号分隔的步骤编码存放，对外以数组形式暴露。
 */
@Entity
@Table(name = "workflow_definition")
public class WorkflowDefinition {

    @Id
    @Column(name = "definition_code", length = 40, nullable = false, updatable = false)
    private String definitionCode;

    @Column(name = "name", length = 120, nullable = false)
    private String name;

    @Column(name = "steps", length = 500, nullable = false)
    private String steps;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected WorkflowDefinition() {
        // for JPA
    }

    public WorkflowDefinition(String definitionCode, String name, List<String> steps, boolean active) {
        this.definitionCode = definitionCode;
        this.name = name;
        this.steps = String.join(",", steps);
        this.active = active;
    }

    public boolean isActive() {
        return active;
    }

    public List<String> stepList() {
        return Arrays.stream(steps.split(","))
                .map(String::trim)
                .filter(step -> !step.isEmpty())
                .toList();
    }

    public String getDefinitionCode() {
        return definitionCode;
    }

    public String getName() {
        return name;
    }

    public String getSteps() {
        return steps;
    }

    public long getVersion() {
        return version;
    }
}
