package com.carddemo.transaction.infrastructure.persistence;

import com.carddemo.transaction.domain.model.PostingBatch;
import com.carddemo.transaction.domain.repository.PageResult;
import com.carddemo.transaction.domain.repository.PostingBatchFilter;
import com.carddemo.transaction.domain.repository.PostingBatchRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public interface PostingBatchJpaRepository
        extends JpaRepository<PostingBatch, String>, JpaSpecificationExecutor<PostingBatch>,
        PostingBatchRepository {

    @Override
    default PostingBatch saveBatch(PostingBatch batch) {
        return saveAndFlush(batch);
    }

    @Override
    default Optional<PostingBatch> findByBatchId(String batchId) {
        return findById(batchId);
    }

    @Override
    Optional<PostingBatch> findByIdempotencyKey(String idempotencyKey);

    @Override
    Optional<PostingBatch> findByReversalOf(String reversalOf);

    @Override
    default PageResult<PostingBatch> search(PostingBatchFilter filter, int page, int size) {
        Sort sort = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("batchId"));
        Page<PostingBatch> result = findAll(specification(filter), PageRequest.of(page, size, sort));
        return new PageResult<>(result.getContent(), result.getTotalElements());
    }

    private static Specification<PostingBatch> specification(PostingBatchFilter filter) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (filter != null && StringUtils.hasText(filter.accountId())) {
                predicates.add(builder.equal(root.get("accountId"), filter.accountId()));
            }
            if (filter != null && StringUtils.hasText(filter.idempotencyKey())) {
                predicates.add(builder.equal(root.get("idempotencyKey"), filter.idempotencyKey()));
            }
            return builder.and(predicates.toArray(new Predicate[0]));
        };
    }
}
