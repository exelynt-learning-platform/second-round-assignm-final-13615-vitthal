package com.ecommerce.service;

import com.ecommerce.dto.CartDto;

public interface CartService {
    CartDto.CartResponse getCart(Long userId);
    CartDto.CartResponse addItem(Long userId, CartDto.AddItemRequest request);
    CartDto.CartResponse updateItem(Long userId, Long productId, CartDto.UpdateItemRequest request);
    CartDto.CartResponse removeItem(Long userId, Long productId);
    void clearCart(Long userId);
}
