package com.ecommerce.service;

import com.ecommerce.dto.OrderDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface OrderService {
    OrderDto.OrderResponse createOrder(Long userId, OrderDto.CreateRequest request);
    OrderDto.OrderResponse getOrderById(Long userId, Long orderId);
    Page<OrderDto.OrderResponse> getUserOrders(Long userId, Pageable pageable);
    OrderDto.PaymentResponse createPaymentIntent(Long userId, Long orderId);
    void handlePaymentSuccess(String paymentIntentId);
    void handlePaymentFailure(String paymentIntentId);
    OrderDto.OrderResponse cancelOrder(Long userId, Long orderId);
}
