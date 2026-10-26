package com.carddemo.account.infrastructure.persistence;

import com.carddemo.account.domain.model.AccountProduct;
import com.carddemo.account.domain.repository.AccountProductRepository;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface AccountProductJpaRepository
        extends JpaRepository<AccountProduct, String>, AccountProductRepository {

    @Override
    default Optional<AccountProduct> findByProductCode(String productCode) {
        return findById(productCode);
    }

    @Override
    @Query("select p from AccountProduct p where p.active = true order by p.productCode")
    List<AccountProduct> findAllActive();

    @Override
    default List<AccountProduct> findAllProducts() {
        return findAll(Sort.by(Sort.Direction.ASC, "productCode"));
    }
}
