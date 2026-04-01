package com.ecommerce.service;

import com.ecommerce.dto.ProductDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;

public interface ProductService {
    ProductDto.Response createProduct(ProductDto.CreateRequest request);
    ProductDto.Response getProductById(Long id);
    Page<ProductDto.Response> getAllProducts(Pageable pageable);
    Page<ProductDto.Response> searchProducts(String query, Pageable pageable);
    Page<ProductDto.Response> getProductsByPriceRange(BigDecimal min, BigDecimal max, Pageable pageable);
    ProductDto.Response updateProduct(Long id, ProductDto.UpdateRequest request);
    void deleteProduct(Long id);
}
