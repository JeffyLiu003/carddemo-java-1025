package com.carddemo.reporting.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * 报表定义：reportCode 为对外标识，sourceService 决定生成时调用哪个下游服务。
 */
@Entity
@Table(name = "report_definition")
public class ReportDefinition {

    @Id
    @Column(name = "report_code", length = 40, nullable = false, updatable = false)
    private String reportCode;

    @Column(name = "name", length = 100, nullable = false)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "source_service", length = 32, nullable = false)
    private String sourceService;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected ReportDefinition() {
        // for JPA
    }

    public String getReportCode() {
        return reportCode;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public String getSourceService() {
        return sourceService;
    }

    public boolean isActive() {
        return active;
    }

    public long getVersion() {
        return version;
    }
}
