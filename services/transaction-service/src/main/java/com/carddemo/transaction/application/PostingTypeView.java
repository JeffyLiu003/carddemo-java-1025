package com.carddemo.transaction.application;

import com.carddemo.transaction.domain.model.PostingRule;
import com.carddemo.transaction.domain.model.PostingType;

import java.util.List;

/**
 * 过账类型视图：类型 + 规则（列表接口返回空规则）。
 */
public record PostingTypeView(PostingType type, List<PostingRule> rules) {
}
