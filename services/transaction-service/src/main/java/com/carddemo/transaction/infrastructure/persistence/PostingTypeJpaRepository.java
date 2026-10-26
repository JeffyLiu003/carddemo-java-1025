package com.carddemo.transaction.infrastructure.persistence;

import com.carddemo.transaction.domain.model.PostingType;
import com.carddemo.transaction.domain.repository.PostingTypeRepository;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PostingTypeJpaRepository extends JpaRepository<PostingType, String>, PostingTypeRepository {

    @Override
    default List<PostingType> findAllTypes() {
        return findAll(Sort.by(Sort.Direction.ASC, "typeCode"));
    }

    @Override
    default Optional<PostingType> findType(String typeCode) {
        return findById(typeCode);
    }
}
