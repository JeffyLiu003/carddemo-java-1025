package com.carddemo.account.domain.repository;

import com.carddemo.account.domain.model.AccountProduct;

import java.util.List;
import java.util.Optional;

/**
 * 账户产品目录查询端口（产品配置由运营维护，账户侧只读）。
 */
public interface AccountProductRepository {

    Optional<AccountProduct> findByProductCode(String productCode);

    List<AccountProduct> findAllActive();

    List<AccountProduct> findAllProducts();
}
