package com.carddemo.notification.infrastructure.persistence;

import com.carddemo.notification.domain.model.Notification;
import com.carddemo.notification.domain.model.NotificationStatus;
import com.carddemo.notification.domain.repository.NotificationRepository;
import com.carddemo.notification.domain.repository.PageResult;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.ArrayList;
import java.util.List;

public interface NotificationJpaRepository
        extends JpaRepository<Notification, String>, JpaSpecificationExecutor<Notification>, NotificationRepository {

    @Override
    default PageResult<Notification> search(NotificationStatus status, int page, int size) {
        Sort sort = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("notificationId"));
        Page<Notification> result = findAll(specification(status), PageRequest.of(page, size, sort));
        return new PageResult<>(result.getContent(), result.getTotalElements());
    }

    private static Specification<Notification> specification(NotificationStatus status) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null) {
                predicates.add(builder.equal(root.get("status"), status));
            }
            return builder.and(predicates.toArray(new Predicate[0]));
        };
    }
}
