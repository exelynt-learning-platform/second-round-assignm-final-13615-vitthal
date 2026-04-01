package com.ecommerce.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class OrderDto {

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateRequest {
        @NotBlank(message = "Shipping address is required")
        private String shippingAddress;

        @NotBlank(message = "Shipping city is required")
        private String shippingCity;

        @NotBlank(message = "Shipping state is required")
        private String shippingState;

        @NotBlank(message = "Shipping zip code is required")
        private String shippingZipCode;

        @NotBlank(message = "Shipping country is required")
        private String shippingCountry;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderItemResponse {
        private Long id;
        private Long productId;
        private String productName;
        private String productImageUrl;
        private int quantity;
        private BigDecimal priceAtPurchase;
        private BigDecimal subtotal;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OrderResponse {
        private Long id;
        private Long userId;
        private List<OrderItemResponse> items;
        private BigDecimal totalPrice;
        private String shippingAddress;
        private String shippingCity;
        private String shippingState;
        private String shippingZipCode;
        private String shippingCountry;
        private String status;
        private String paymentStatus;
        private String stripeClientSecret;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaymentResponse {
        private Long orderId;
        private String clientSecret;
        private String paymentIntentId;
        private String status;
        private BigDecimal amount;
        private String currency;
    }
}
