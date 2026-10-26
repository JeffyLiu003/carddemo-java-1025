package com.carddemo.account.application;

import com.carddemo.account.domain.exception.NotFoundException;
import com.carddemo.account.domain.model.AccountProduct;
import com.carddemo.account.domain.repository.AccountProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 账户产品目录查询。
 */
@Service
@Transactional(readOnly = true)
public class AccountProductQueryService {

    private final AccountProductRepository products;

    public AccountProductQueryService(AccountProductRepository products) {
        this.products = products;
    }

    public List<AccountProduct> findAll() {
        return products.findAllProducts();
    }

    public AccountProduct getByCode(String productCode) {
        return products.findByProductCode(productCode)
                .orElseThrow(() -> new NotFoundException("PRODUCT_NOT_FOUND",
                        "Account product " + productCode + " not found"));
    }
}
