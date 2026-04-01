package com.ecommerce.service;

import com.ecommerce.dto.ProductDto;
import com.ecommerce.entity.Product;
import com.ecommerce.exception.ResourceNotFoundException;
import com.ecommerce.repository.ProductRepository;
import com.ecommerce.service.impl.ProductServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProductService Unit Tests")
class ProductServiceTest {

    @Mock ProductRepository productRepository;

    @InjectMocks ProductServiceImpl productService;

    private Product activeProduct;
    private Pageable pageable;

    @BeforeEach
    void setUp() {
        activeProduct = Product.builder()
                .id(1L).name("Laptop Pro").description("High-end laptop")
                .price(new BigDecimal("1299.99")).stockQuantity(10)
                .imageUrl("https://example.com/img.jpg").active(true).build();
        pageable = PageRequest.of(0, 10, Sort.by("createdAt").descending());
    }

    // ─── Create ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("createProduct: saves and returns product response")
    void createProduct_success() {
        when(productRepository.save(any(Product.class))).thenReturn(activeProduct);

        ProductDto.CreateRequest request = new ProductDto.CreateRequest(
                "Laptop Pro", "High-end laptop", new BigDecimal("1299.99"),
                10, "https://example.com/img.jpg");

        ProductDto.Response response = productService.createProduct(request);

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getName()).isEqualTo("Laptop Pro");
        assertThat(response.getPrice()).isEqualByComparingTo("1299.99");
        assertThat(response.isInStock()).isTrue();
        verify(productRepository).save(any(Product.class));
    }

    // ─── Get By ID ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getProductById: returns active product")
    void getProductById_active_returnsProduct() {
        when(productRepository.findById(1L)).thenReturn(Optional.of(activeProduct));

        ProductDto.Response response = productService.getProductById(1L);

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.isActive()).isTrue();
    }

    @Test
    @DisplayName("getProductById: throws for inactive (soft-deleted) product")
    void getProductById_inactive_throwsNotFound() {
        activeProduct.setActive(false);
        when(productRepository.findById(1L)).thenReturn(Optional.of(activeProduct));

        assertThatThrownBy(() -> productService.getProductById(1L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("getProductById: throws for missing product")
    void getProductById_missing_throwsNotFound() {
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.getProductById(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ─── Get All ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getAllProducts: returns paginated list of active products")
    void getAllProducts_returnsPaginatedProducts() {
        Page<Product> productPage = new PageImpl<>(List.of(activeProduct));
        when(productRepository.findByActiveTrue(pageable)).thenReturn(productPage);

        Page<ProductDto.Response> result = productService.getAllProducts(pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getName()).isEqualTo("Laptop Pro");
    }

    // ─── Update ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("updateProduct: partial update applies only non-null fields")
    void updateProduct_partialUpdate() {
        when(productRepository.findById(1L)).thenReturn(Optional.of(activeProduct));
        when(productRepository.save(any(Product.class))).thenReturn(activeProduct);

        ProductDto.UpdateRequest request = new ProductDto.UpdateRequest();
        request.setPrice(new BigDecimal("999.99"));

        ProductDto.Response response = productService.updateProduct(1L, request);

        assertThat(response.getPrice()).isEqualByComparingTo("999.99");
        assertThat(response.getName()).isEqualTo("Laptop Pro"); // unchanged
    }

    @Test
    @DisplayName("updateProduct: throws for unknown product")
    void updateProduct_unknownProduct_throwsNotFound() {
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                productService.updateProduct(99L, new ProductDto.UpdateRequest()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ─── Delete ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("deleteProduct: soft-deletes by setting active=false")
    void deleteProduct_softDeletes() {
        when(productRepository.findById(1L)).thenReturn(Optional.of(activeProduct));
        when(productRepository.save(any(Product.class))).thenReturn(activeProduct);

        productService.deleteProduct(1L);

        assertThat(activeProduct.isActive()).isFalse();
        verify(productRepository).save(activeProduct);
    }

    @Test
    @DisplayName("deleteProduct: throws for unknown product")
    void deleteProduct_unknownProduct_throwsNotFound() {
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.deleteProduct(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ─── inStock flag ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("getProductById: inStock is false when stockQuantity is 0")
    void getProductById_outOfStock_flaggedCorrectly() {
        activeProduct.setStockQuantity(0);
        when(productRepository.findById(1L)).thenReturn(Optional.of(activeProduct));

        ProductDto.Response response = productService.getProductById(1L);

        assertThat(response.isInStock()).isFalse();
        assertThat(response.getStockQuantity()).isZero();
    }
}
