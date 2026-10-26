package com.carddemo.transaction.application;

import com.carddemo.transaction.domain.exception.NotFoundException;
import com.carddemo.transaction.domain.model.PostingType;
import com.carddemo.transaction.domain.repository.PostingRuleRepository;
import com.carddemo.transaction.domain.repository.PostingTypeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 过账类型查询用例：列表不含规则，详情附带规则（契约 §2.3）。
 */
@Service
@Transactional(readOnly = true)
public class PostingTypeService {

    private final PostingTypeRepository postingTypes;
    private final PostingRuleRepository postingRules;

    public PostingTypeService(PostingTypeRepository postingTypes, PostingRuleRepository postingRules) {
        this.postingTypes = postingTypes;
        this.postingRules = postingRules;
    }

    public List<PostingTypeView> list() {
        return postingTypes.findAllTypes().stream()
                .map(type -> new PostingTypeView(type, List.of()))
                .toList();
    }

    public PostingTypeView get(String typeCode) {
        PostingType type = postingTypes.findType(typeCode)
                .orElseThrow(() -> new NotFoundException("POSTING_TYPE_NOT_FOUND",
                        "Posting type " + typeCode + " not found"));
        return new PostingTypeView(type, postingRules.findRules(type.getTypeCode()));
    }
}
