package com.carddemo.aml.domain.service;

import com.carddemo.aml.domain.model.NameNormalizer;
import com.carddemo.aml.domain.model.SanctionsEntry;
import com.carddemo.aml.domain.model.ScreeningResultStatus;

import java.util.List;

/**
 * 纯筛查领域服务。
 *
 * <p>归一化后完全相等 → BLOCKED（100 分）；双向包含（录入名包含名单名或反之）→ POTENTIAL_MATCH（70 分）；
 * 否则 CLEAR（0 分）。多个部分命中时取名单名最长者，避免短名单名（如单个词）抢走更精确的命中。
 */
public class SanctionsScreeningService {

    static final int BLOCKED_SCORE = 100;
    static final int POTENTIAL_MATCH_SCORE = 70;

    public ScreeningMatch screen(String name, List<SanctionsEntry> entries) {
        String normalized = NameNormalizer.normalize(name);
        SanctionsEntry exact = null;
        SanctionsEntry partial = null;
        for (SanctionsEntry entry : entries) {
            if (!entry.isActive()) {
                continue;
            }
            String target = entry.getNormalizedName();
            if (target.isEmpty()) {
                continue;
            }
            if (target.equals(normalized)) {
                exact = entry;
                break;
            }
            if (target.contains(normalized) || normalized.contains(target)) {
                if (partial == null
                        || target.length() > partial.getNormalizedName().length()) {
                    partial = entry;
                }
            }
        }
        if (exact != null) {
            return new ScreeningMatch(ScreeningResultStatus.BLOCKED, exact, BLOCKED_SCORE);
        }
        if (partial != null) {
            return new ScreeningMatch(ScreeningResultStatus.POTENTIAL_MATCH, partial, POTENTIAL_MATCH_SCORE);
        }
        return new ScreeningMatch(ScreeningResultStatus.CLEAR, null, 0);
    }
}
