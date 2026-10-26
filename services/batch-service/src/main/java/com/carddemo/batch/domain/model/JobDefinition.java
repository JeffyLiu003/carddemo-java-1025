package com.carddemo.batch.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * 作业定义：jobCode 为对外标识，targetService 决定执行时调用哪个下游服务。
 */
@Entity
@Table(name = "job_definition")
public class JobDefinition {

    @Id
    @Column(name = "job_code", length = 32, nullable = false, updatable = false)
    private String jobCode;

    @Column(name = "name", length = 100, nullable = false)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "target_service", length = 32, nullable = false)
    private String targetService;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected JobDefinition() {
        // for JPA
    }

    public String getJobCode() {
        return jobCode;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getTargetService() {
        return targetService;
    }

    public boolean isActive() {
        return active;
    }

    public long getVersion() {
        return version;
    }
}
