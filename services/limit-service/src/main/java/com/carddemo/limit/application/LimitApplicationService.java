package com.carddemo.limit.application;

import com.carddemo.limit.domain.exception.BusinessRuleException;
import com.carddemo.limit.domain.exception.InvalidRequestException;
import com.carddemo.limit.domain.exception.NotFoundException;
import com.carddemo.limit.domain.model.LimitDefinition;
import com.carddemo.limit.domain.model.LimitType;
import com.carddemo.limit.domain.model.ScopeType;
import com.carddemo.limit.domain.repository.LimitDefinitionRepository;
import com.carddemo.limit.domain.repository.PageResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 限额用例：创建、查询、调整、占用、释放与可用性检查。
 */
@Service
@Transactional
public class LimitApplicationService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final BigDecimal ZERO = new BigDecimal("0.00");
    private static final Pattern CURRENCY_PATTERN = Pattern.compile("^[A-Z]{3}$");

    private final LimitDefinitionRepository limits;

    public LimitApplicationService(LimitDefinitionRepository limits) {
        this.limits = limits;
    }

    /**
     * 创建限额。同一 (scopeType, scopeId, limitType, currency) 只允许一条定义。
     */
    public LimitDefinition create(CreateLimitCommand command) {
        ScopeType scopeType = requireScopeType(command.scopeType());
        LimitType limitType = requireLimitType(command.limitType());
        String scopeId = requireScopeId(command.scopeId());
        String currency = normalizeCurrency(command.currency());

        Optional<LimitDefinition> existing = limits.findByScope(scopeType, scopeId, limitType, currency);
        if (existing.isPresent()) {
            throw new BusinessRuleException("LIMIT_ALREADY_DEFINED",
                    "Limit for " + scopeType + "/" + scopeId + "/" + limitType + "/" + currency
                            + " already exists.");
        }

        LimitDefinition definition = LimitDefinition.create(UUID.randomUUID().toString(), scopeType, scopeId,
                limitType, currency, command.amount(), command.active());
        return limits.save(definition);
    }

    @Transactional(readOnly = true)
    public LimitDefinition get(String limitId) {
        return requireLimit(limitId);
    }

    @Transactional(readOnly = true)
    public PageResult<LimitDefinition> list(ScopeType scopeType, String scopeId, int page, int size) {
        int safeSize = size <= 0 ? DEFAULT_PAGE_SIZE : size;
        return limits.search(scopeType, scopeId, Math.max(page, 0), safeSize);
    }

    /**
     * 调整额度：不得低于已用金额，否则 409 LIMIT_BELOW_USED。
     */
    public LimitDefinition adjust(String limitId, BigDecimal amount) {
        LimitDefinition definition = lockLimit(limitId);
        definition.adjust(amount);
        return limits.save(definition);
    }

    /**
     * 占用额度：超过剩余额度 409 LIMIT_EXCEEDED。
     */
    public LimitDefinition reserve(String limitId, BigDecimal amount) {
        LimitDefinition definition = lockLimit(limitId);
        definition.reserve(amount);
        return limits.save(definition);
    }

    /**
     * 释放额度：释放金额不得超过已用金额，否则 409 INVALID_LIMIT_RELEASE。
     */
    public LimitDefinition release(String limitId, BigDecimal amount) {
        LimitDefinition definition = lockLimit(limitId);
        definition.release(amount);
        return limits.save(definition);
    }

    /**
     * 可用性检查。不存在匹配限额时返回 allowed=false、remainingAmount=0。
     */
    @Transactional(readOnly = true)
    public LimitCheckView check(ScopeType scopeType, String scopeId, LimitType limitType,
                                String currency, BigDecimal amount) {
        ScopeType scope = requireScopeType(scopeType);
        LimitType type = requireLimitType(limitType);
        String id = requireScopeId(scopeId);
        String normalizedCurrency = normalizeCurrency(currency);
        BigDecimal requested = amount == null ? null : amount.setScale(2, RoundingMode.HALF_UP);
        if (requested == null || requested.signum() <= 0) {
            throw new InvalidRequestException("INVALID_AMOUNT", "Checked amount must be greater than zero.");
        }

        return limits.findByScope(scope, id, type, normalizedCurrency)
                .map(definition -> new LimitCheckView(
                        definition.allows(requested),
                        definition.getLimitId(),
                        scope,
                        id,
                        type,
                        normalizedCurrency,
                        requested,
                        definition.getUsedAmount(),
                        definition.remainingAmount()))
                .orElseGet(() -> new LimitCheckView(false, null, scope, id, type, normalizedCurrency,
                        requested, ZERO, ZERO));
    }

    private LimitDefinition requireLimit(String limitId) {
        return limits.findByLimitId(limitId)
                .orElseThrow(() -> new NotFoundException("LIMIT_NOT_FOUND",
                        "Limit " + limitId + " not found"));
    }

    private LimitDefinition lockLimit(String limitId) {
        return limits.lockByLimitId(limitId)
                .orElseThrow(() -> new NotFoundException("LIMIT_NOT_FOUND",
                        "Limit " + limitId + " not found"));
    }

    private static ScopeType requireScopeType(ScopeType scopeType) {
        if (scopeType == null) {
            throw new InvalidRequestException("INVALID_LIMIT_SCOPE", "Limit scope type must be supplied.");
        }
        return scopeType;
    }

    private static LimitType requireLimitType(LimitType limitType) {
        if (limitType == null) {
            throw new InvalidRequestException("INVALID_LIMIT_TYPE", "Limit type must be supplied.");
        }
        return limitType;
    }

    private static String requireScopeId(String scopeId) {
        if (!StringUtils.hasText(scopeId)) {
            throw new InvalidRequestException("INVALID_LIMIT_SCOPE", "Limit scope id must be supplied.");
        }
        return scopeId.trim();
    }

    private static String normalizeCurrency(String currency) {
        if (!StringUtils.hasText(currency)) {
            throw new InvalidRequestException("INVALID_CURRENCY", "Limit currency must be supplied.");
        }
        String normalized = currency.trim().toUpperCase(Locale.ROOT);
        if (!CURRENCY_PATTERN.matcher(normalized).matches()) {
            throw new InvalidRequestException("INVALID_CURRENCY",
                    "Limit currency must be a 3-letter ISO code, got " + currency + ".");
        }
        return normalized;
    }
}
