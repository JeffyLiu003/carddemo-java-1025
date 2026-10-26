package com.carddemo.marketing.interfaces.rest.dto;

import com.carddemo.marketing.application.EligibilityView;

import java.util.List;

/**
 * 资格列表（不分页：ACTIVE 活动数量有限，一次返回便于前台展示）。
 */
public record EligibilityListResponse(List<EligibilityView> items) {

    public static EligibilityListResponse from(List<EligibilityView> items) {
        return new EligibilityListResponse(items);
    }
}
