package com.ecommerce.service.impl;

import com.ecommerce.dto.CartDto;
import com.ecommerce.entity.Cart;
import com.ecommerce.entity.CartItem;
import com.ecommerce.entity.Product;
import com.ecommerce.exception.BusinessException;
import com.ecommerce.exception.ResourceNotFoundException;
import com.ecommerce.repository.CartItemRepository;
import com.ecommerce.repository.CartRepository;
import com.ecommerce.repository.ProductRepository;
import com.ecommerce.service.CartService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CartServiceImpl implements CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;

    @Override
    @Transactional(readOnly = true)
    public CartDto.CartResponse getCart(Long userId) {
        Cart cart = getOrCreateCart(userId);
        return toResponse(cart);
    }

    @Override
    @Transactional
    public CartDto.CartResponse addItem(Long userId, CartDto.AddItemRequest request) {
        Cart cart = getOrCreateCart(userId);
        Product product = getActiveProduct(request.getProductId());

        validateStockAvailability(product, request.getQuantity());

        Optional<CartItem> existingItem =
                cartItemRepository.findByCartIdAndProductId(cart.getId(), product.getId());

        if (existingItem.isPresent()) {
            CartItem item = existingItem.get();
            int newQty = item.getQuantity() + request.getQuantity();
            validateStockAvailability(product, newQty);
            item.setQuantity(newQty);
            cartItemRepository.save(item);
        } else {
            CartItem newItem = CartItem.builder()
                    .cart(cart)
                    .product(product)
                    .quantity(request.getQuantity())
                    .build();
            cart.getItems().add(newItem);
            cartItemRepository.save(newItem);
        }

        log.info("Item added to cart: userId={}, productId={}", userId, product.getId());
        return toResponse(cartRepository.findById(cart.getId()).orElse(cart));
    }

    @Override
    @Transactional
    public CartDto.CartResponse updateItem(Long userId, Long productId,
                                           CartDto.UpdateItemRequest request) {
        Cart cart = getCartByUserId(userId);
        Product product = getActiveProduct(productId);

        CartItem item = cartItemRepository.findByCartIdAndProductId(cart.getId(), productId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Product not found in cart: " + productId));

        validateStockAvailability(product, request.getQuantity());
        item.setQuantity(request.getQuantity());
        cartItemRepository.save(item);

        return toResponse(cartRepository.findById(cart.getId()).orElse(cart));
    }

    @Override
    @Transactional
    public CartDto.CartResponse removeItem(Long userId, Long productId) {
        Cart cart = getCartByUserId(userId);

        cartItemRepository.findByCartIdAndProductId(cart.getId(), productId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Product not found in cart: " + productId));

        cartItemRepository.deleteByCartIdAndProductId(cart.getId(), productId);
        log.info("Item removed from cart: userId={}, productId={}", userId, productId);

        return toResponse(cartRepository.findById(cart.getId()).orElse(cart));
    }

    @Override
    @Transactional
    public void clearCart(Long userId) {
        Cart cart = getCartByUserId(userId);
        cart.getItems().clear();
        cartRepository.save(cart);
        log.info("Cart cleared for userId={}", userId);
    }

    // ─── Helpers ───────────────────────────────────────────────────────────────

    private Cart getOrCreateCart(Long userId) {
        return cartRepository.findByUserId(userId).orElseGet(() -> {
            // This shouldn't normally happen — cart is created on registration
            Cart newCart = Cart.builder().build();
            return cartRepository.save(newCart);
        });
    }

    private Cart getCartByUserId(Long userId) {
        return cartRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart not found for user: " + userId));
    }

    private Product getActiveProduct(Long productId) {
        return productRepository.findById(productId)
                .filter(Product::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Product", productId));
    }

    private void validateStockAvailability(Product product, int requestedQty) {
        if (product.getStockQuantity() < requestedQty) {
            throw new BusinessException(
                    String.format("Insufficient stock for '%s'. Available: %d, Requested: %d",
                            product.getName(), product.getStockQuantity(), requestedQty));
        }
    }

    private CartDto.CartResponse toResponse(Cart cart) {
        List<CartDto.CartItemResponse> itemResponses = cart.getItems().stream()
                .map(item -> CartDto.CartItemResponse.builder()
                        .id(item.getId())
                        .productId(item.getProduct().getId())
                        .productName(item.getProduct().getName())
                        .productImageUrl(item.getProduct().getImageUrl())
                        .unitPrice(item.getProduct().getPrice())
                        .quantity(item.getQuantity())
                        .subtotal(item.getProduct().getPrice()
                                .multiply(java.math.BigDecimal.valueOf(item.getQuantity())))
                        .build())
                .collect(Collectors.toList());

        return CartDto.CartResponse.builder()
                .id(cart.getId())
                .items(itemResponses)
                .totalItems(cart.getTotalItems())
                .totalPrice(cart.getTotalPrice())
                .build();
    }
}
