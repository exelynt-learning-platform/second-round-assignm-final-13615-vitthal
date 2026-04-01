package com.ecommerce.service;

import com.ecommerce.dto.CartDto;
import com.ecommerce.entity.Cart;
import com.ecommerce.entity.CartItem;
import com.ecommerce.entity.Product;
import com.ecommerce.exception.BusinessException;
import com.ecommerce.exception.ResourceNotFoundException;
import com.ecommerce.repository.CartItemRepository;
import com.ecommerce.repository.CartRepository;
import com.ecommerce.repository.ProductRepository;
import com.ecommerce.service.impl.CartServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CartService Unit Tests")
class CartServiceTest {

    @Mock CartRepository cartRepository;
    @Mock CartItemRepository cartItemRepository;
    @Mock ProductRepository productRepository;

    @InjectMocks CartServiceImpl cartService;

    private Product product;
    private Cart cart;

    @BeforeEach
    void setUp() {
        product = Product.builder()
                .id(10L).name("Widget").price(new BigDecimal("29.99"))
                .stockQuantity(50).active(true).build();

        cart = Cart.builder().id(1L).items(new ArrayList<>()).build();
    }

    // ─── Get Cart ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getCart: returns cart for existing user")
    void getCart_existingUser_returnsCart() {
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(cart));

        CartDto.CartResponse response = cartService.getCart(1L);

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getItems()).isEmpty();
        assertThat(response.getTotalPrice()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    // ─── Add Item ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("addItem: adds new product to empty cart")
    void addItem_newProduct_addsSuccessfully() {
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(cart));
        when(productRepository.findById(10L)).thenReturn(Optional.of(product));
        when(cartItemRepository.findByCartIdAndProductId(1L, 10L)).thenReturn(Optional.empty());
        when(cartItemRepository.save(any(CartItem.class))).thenAnswer(inv -> inv.getArgument(0));
        when(cartRepository.findById(1L)).thenReturn(Optional.of(cart));

        CartDto.AddItemRequest request = new CartDto.AddItemRequest(10L, 2);
        cartService.addItem(1L, request);

        verify(cartItemRepository).save(any(CartItem.class));
    }

    @Test
    @DisplayName("addItem: increments quantity when product already in cart")
    void addItem_existingProduct_incrementsQuantity() {
        CartItem existingItem = CartItem.builder()
                .id(5L).cart(cart).product(product).quantity(3).build();
        cart.getItems().add(existingItem);

        when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(cart));
        when(productRepository.findById(10L)).thenReturn(Optional.of(product));
        when(cartItemRepository.findByCartIdAndProductId(1L, 10L))
                .thenReturn(Optional.of(existingItem));
        when(cartItemRepository.save(any(CartItem.class))).thenAnswer(inv -> inv.getArgument(0));
        when(cartRepository.findById(1L)).thenReturn(Optional.of(cart));

        cartService.addItem(1L, new CartDto.AddItemRequest(10L, 2));

        assertThat(existingItem.getQuantity()).isEqualTo(5); // 3 + 2
    }

    @Test
    @DisplayName("addItem: throws BusinessException when stock is insufficient")
    void addItem_insufficientStock_throwsBusinessException() {
        product.setStockQuantity(1);
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(cart));
        when(productRepository.findById(10L)).thenReturn(Optional.of(product));

        assertThatThrownBy(() ->
                cartService.addItem(1L, new CartDto.AddItemRequest(10L, 5)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Insufficient stock");

        verify(cartItemRepository, never()).save(any());
    }

    @Test
    @DisplayName("addItem: throws ResourceNotFoundException for inactive product")
    void addItem_inactiveProduct_throwsNotFound() {
        product.setActive(false);
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(cart));
        when(productRepository.findById(10L)).thenReturn(Optional.of(product));

        assertThatThrownBy(() ->
                cartService.addItem(1L, new CartDto.AddItemRequest(10L, 1)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ─── Update Item ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("updateItem: changes quantity of existing cart item")
    void updateItem_success() {
        CartItem item = CartItem.builder()
                .id(5L).cart(cart).product(product).quantity(2).build();
        cart.getItems().add(item);

        when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(cart));
        when(productRepository.findById(10L)).thenReturn(Optional.of(product));
        when(cartItemRepository.findByCartIdAndProductId(1L, 10L)).thenReturn(Optional.of(item));
        when(cartItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(cartRepository.findById(1L)).thenReturn(Optional.of(cart));

        cartService.updateItem(1L, 10L, new CartDto.UpdateItemRequest(7));

        assertThat(item.getQuantity()).isEqualTo(7);
    }

    @Test
    @DisplayName("updateItem: throws when item not in cart")
    void updateItem_itemNotInCart_throwsNotFound() {
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(cart));
        when(productRepository.findById(10L)).thenReturn(Optional.of(product));
        when(cartItemRepository.findByCartIdAndProductId(1L, 10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                cartService.updateItem(1L, 10L, new CartDto.UpdateItemRequest(3)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ─── Remove Item ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("removeItem: removes product from cart")
    void removeItem_success() {
        CartItem item = CartItem.builder().id(5L).cart(cart).product(product).quantity(2).build();
        cart.getItems().add(item);

        when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findByCartIdAndProductId(1L, 10L)).thenReturn(Optional.of(item));
        when(cartRepository.findById(1L)).thenReturn(Optional.of(cart));

        cartService.removeItem(1L, 10L);

        verify(cartItemRepository).deleteByCartIdAndProductId(1L, 10L);
    }

    @Test
    @DisplayName("removeItem: throws when product not in cart")
    void removeItem_notInCart_throwsNotFound() {
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(cart));
        when(cartItemRepository.findByCartIdAndProductId(1L, 99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> cartService.removeItem(1L, 99L))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(cartItemRepository, never()).deleteByCartIdAndProductId(any(), any());
    }

    // ─── Clear Cart ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("clearCart: removes all items from cart")
    void clearCart_success() {
        CartItem item1 = CartItem.builder().id(1L).cart(cart).product(product).quantity(2).build();
        CartItem item2 = CartItem.builder().id(2L).cart(cart).product(product).quantity(1).build();
        cart.getItems().addAll(List.of(item1, item2));

        when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(cart));
        when(cartRepository.save(any(Cart.class))).thenReturn(cart);

        cartService.clearCart(1L);

        assertThat(cart.getItems()).isEmpty();
        verify(cartRepository).save(cart);
    }
}
