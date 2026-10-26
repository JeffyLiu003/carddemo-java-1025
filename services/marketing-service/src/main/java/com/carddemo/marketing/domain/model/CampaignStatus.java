package com.carddemo.marketing.domain.model;

import com.carddemo.marketing.domain.exception.InvalidRequestException;

import java.util.Locale;

/**
 * 活动状态（设计 §3.1）。只允许 DRAFT/PAUSED → ACTIVE → PAUSED → ENDED 的受控迁移。
 */
public enum CampaignStatus {
    DRAFT,
    ACTIVE,
    PAUSED,
    ENDED;

    public boolean isActive() {
        return this == ACTIVE;
    }

    /**
     * 查询参数解析：未提供时返回 null（不过滤），非法值映射为 HTTP 400。
     */
    public static CampaignStatus parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return CampaignStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new InvalidRequestException("INVALID_CAMPAIGN_STATUS",
                    "Campaign status " + value + " is not one of DRAFT, ACTIVE, PAUSED, ENDED.");
        }
    }
}
