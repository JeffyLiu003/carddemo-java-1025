package com.carddemo.aml.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * 制裁/名单条目。normalized_name 落库以保证筛查时不再重复归一化。
 */
@Entity
@Table(name = "sanctions_entry")
public class SanctionsEntry {

    @Id
    @Column(name = "entry_id", length = 36, nullable = false, updatable = false)
    private String entryId;

    @Column(name = "name", length = 100, nullable = false)
    private String name;

    @Column(name = "normalized_name", length = 100, nullable = false)
    private String normalizedName;

    @Column(name = "country", length = 3)
    private String country;

    @Column(name = "entity_type", length = 24)
    private String entityType;

    @Column(name = "list_source", length = 32)
    private String listSource;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected SanctionsEntry() {
        // for JPA
    }

    public SanctionsEntry(String entryId, String name, String country, String entityType,
                          String listSource) {
        this.entryId = entryId;
        this.name = name;
        this.normalizedName = NameNormalizer.normalize(name);
        this.country = country;
        this.entityType = entityType;
        this.listSource = listSource;
        this.active = true;
    }

    public String getEntryId() {
        return entryId;
    }

    public String getName() {
        return name;
    }

    /**
     * 历史数据 normalized_name 缺失时回退到实时归一化。
     */
    public String getNormalizedName() {
        return normalizedName == null ? NameNormalizer.normalize(name) : normalizedName;
    }

    public String getCountry() {
        return country;
    }

    public String getEntityType() {
        return entityType;
    }

    public String getListSource() {
        return listSource;
    }

    public boolean isActive() {
        return active;
    }

    public long getVersion() {
        return version;
    }
}
