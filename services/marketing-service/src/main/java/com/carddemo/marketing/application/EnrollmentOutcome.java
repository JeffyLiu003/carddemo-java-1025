package com.carddemo.marketing.application;

import com.carddemo.marketing.domain.model.Enrollment;

/**
 * 报名结果：created=false 表示重复报名返回已有记录（幂等，接口回 200）。
 */
public record EnrollmentOutcome(Enrollment enrollment, boolean created) {
}
