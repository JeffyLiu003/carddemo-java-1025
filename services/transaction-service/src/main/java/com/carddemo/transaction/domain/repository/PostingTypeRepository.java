package com.carddemo.transaction.domain.repository;

import com.carddemo.transaction.domain.model.PostingType;

import java.util.List;
import java.util.Optional;

/**
 * 过账类型仓储端口。
 */
public interface PostingTypeRepository {

    List<PostingType> findAllTypes();

    Optional<PostingType> findType(String typeCode);
}
