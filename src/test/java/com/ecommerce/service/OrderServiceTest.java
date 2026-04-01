package com.ecommerce.service;

import com.ecommerce.dto.OrderDto;
import com.ecommerce.entity.*;
import com.ecommerce.exception.BusinessException;
import com.ecommerce.exception.ResourceNotFoundException;
import com.ecommerce.repository.*;
import com.ecommerce.service.impl.OrderServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderService Unit Tests")
class OrderServiceTest {

    @Mock OrderRepository orderRepository;
    @Mock CartRepository cartRepository;
    @Mock ProductRepository productRepository;
    @Mock UserRepository userRepository;

    @InjectMocks OrderServiceImpl orderService;

    private User user;
    private Product product;
    private Cart cart;
    private CartItem cartItem;
    private OrderDto.CreateRequest createRequest;

    @BeforeEach
    void setUp() {
        user = User.builder().id(1L).email("jane@example.com")
                .firstName("Jane").lastName("Doe").role(User.Role.USER).build();

        product = Product.builder()
                .id(10L).name("Widget")
                .price(new BigDecimal("49.99"))
                .stockQuantity(20).active(true).build();

        cart = Cart.builder().id(1L).user(user).items(new ArrayList<>()).build();
        cartItem = CartItem.builder().id(1L).cart(cart).product(product).quantity(2).build();
        cart.getItems().add(cartItem);

        createRequest = new OrderDto.CreateRequest(
                "123 Main St", "Springfield", "IL", "62701", "US");
    }

    // ─── Create Order ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("createOrder: success creates order, deducts stock, clears cart")
    void createOrder_success() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(cart));
        when(productRepository.save(any(Product.class))).thenReturn(product);
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            o.setId(100L);
            return o;
        });
        when(cartRepository.save(any(Cart.class))).thenReturn(cart);

        OrderDto.OrderResponse response = orderService.createOrder(1L, createRequest);

        assertThat(response.getId()).isEqualTo(100L);
        assertThat(response.getTotalPrice()).isEqualByComparingTo("99.98"); // 2 × 49.99
        assertThat(response.getStatus()).isEqualTo("PENDING");
        assertThat(response.getPaymentStatus()).isEqualTo("PENDING");

        // Stock reduced by 2
        assertThat(product.getStockQuantity()).isEqualTo(18);

        // Cart cleared
        assertThat(cart.getItems()).isEmpty();

        verify(orderRepository).save(any(Order.class));
        verify(cartRepository).save(cart);
    }

    @Test
    @DisplayName("createOrder: empty cart throws BusinessException")
    void createOrder_emptyCart_throwsBusinessException() {
        cart.getItems().clear();
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(cart));

        assertThatThrownBy(() -> orderService.createOrder(1L, createRequest))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("empty cart");

        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("createOrder: insufficient stock throws BusinessException")
    void createOrder_insufficientStock_throwsBusinessException() {
        product.setStockQuantity(1); // cart wants 2
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(cartRepository.findByUserId(1L)).thenReturn(Optional.of(cart));

        assertThatThrownBy(() -> orderService.createOrder(1L, createRequest))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Insufficient stock");

        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("createOrder: user not found throws ResourceNotFoundException")
    void createOrder_userNotFound_throwsNotFound() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.createOrder(99L, createRequest))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ─── Get Order ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getOrderById: returns order when user owns it")
    void getOrderById_success() {
        Order order = buildPersistedOrder(100L);
        when(orderRepository.findByIdAndUserId(100L, 1L)).thenReturn(Optional.of(order));

        OrderDto.OrderResponse response = orderService.getOrderById(1L, 100L);

        assertThat(response.getId()).isEqualTo(100L);
        assertThat(response.getUserId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("getOrderById: throws when order belongs to different user")
    void getOrderById_wrongUser_throwsNotFound() {
        when(orderRepository.findByIdAndUserId(100L, 2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.getOrderById(2L, 100L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ─── Get User Orders ────────────────────────────────────────────────────────

    @Test
    @DisplayName("getUserOrders: returns paginated orders for user")
    void getUserOrders_returnsPaginatedList() {
        Order order = buildPersistedOrder(100L);
        Page<Order> page = new PageImpl<>(List.of(order));
        when(orderRepository.findByUserId(eq(1L), any(PageRequest.class))).thenReturn(page);

        Page<OrderDto.OrderResponse> result =
                orderService.getUserOrders(1L, PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getId()).isEqualTo(100L);
    }

    // ─── Cancel Order ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("cancelOrder: cancels a PENDING order and restores stock")
    void cancelOrder_pendingOrder_successAndRestoresStock() {
        Order order = buildPersistedOrder(100L);
        order.setStatus(Order.OrderStatus.PENDING);
        int originalStock = product.getStockQuantity();

        when(orderRepository.findByIdAndUserId(100L, 1L)).thenReturn(Optional.of(order));
        when(productRepository.save(any(Product.class))).thenReturn(product);
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        OrderDto.OrderResponse response = orderService.cancelOrder(1L, 100L);

        assertThat(response.getStatus()).isEqualTo("CANCELLED");
        assertThat(product.getStockQuantity()).isEqualTo(originalStock + 2); // quantity restored
    }

    @Test
    @DisplayName("cancelOrder: cannot cancel a SHIPPED order")
    void cancelOrder_shippedOrder_throwsBusinessException() {
        Order order = buildPersistedOrder(100L);
        order.setStatus(Order.OrderStatus.SHIPPED);

        when(orderRepository.findByIdAndUserId(100L, 1L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.cancelOrder(1L, 100L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already been shipped");
    }

    @Test
    @DisplayName("cancelOrder: cannot cancel an already cancelled order")
    void cancelOrder_alreadyCancelled_throwsBusinessException() {
        Order order = buildPersistedOrder(100L);
        order.setStatus(Order.OrderStatus.CANCELLED);

        when(orderRepository.findByIdAndUserId(100L, 1L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.cancelOrder(1L, 100L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already cancelled");
    }

    // ─── Payment Webhook Handlers ────────────────────────────────────────────────

    @Test
    @DisplayName("handlePaymentSuccess: updates order to COMPLETED + CONFIRMED")
    void handlePaymentSuccess_updatesOrderStatus() {
        Order order = buildPersistedOrder(100L);
        order.setStripePaymentIntentId("pi_test_123");
        order.setPaymentStatus(Order.PaymentStatus.PROCESSING);

        when(orderRepository.findByStripePaymentIntentId("pi_test_123"))
                .thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        orderService.handlePaymentSuccess("pi_test_123");

        assertThat(order.getPaymentStatus()).isEqualTo(Order.PaymentStatus.COMPLETED);
        assertThat(order.getStatus()).isEqualTo(Order.OrderStatus.CONFIRMED);
    }

    @Test
    @DisplayName("handlePaymentFailure: marks order payment as FAILED")
    void handlePaymentFailure_updatesPaymentStatus() {
        Order order = buildPersistedOrder(100L);
        order.setStripePaymentIntentId("pi_test_fail");

        when(orderRepository.findByStripePaymentIntentId("pi_test_fail"))
                .thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        orderService.handlePaymentFailure("pi_test_fail");

        assertThat(order.getPaymentStatus()).isEqualTo(Order.PaymentStatus.FAILED);
    }

    // ─── Helpers ────────────────────────────────────────────────────────────────

    private Order buildPersistedOrder(Long orderId) {
        OrderItem orderItem = OrderItem.builder()
                .id(1L).product(product).quantity(2)
                .priceAtPurchase(product.getPrice()).build();

        Order order = Order.builder()
                .id(orderId).user(user)
                .items(new ArrayList<>(List.of(orderItem)))
                .totalPrice(new BigDecimal("99.98"))
                .shippingAddress("123 Main St").shippingCity("Springfield")
                .shippingState("IL").shippingZipCode("62701").shippingCountry("US")
                .status(Order.OrderStatus.PENDING)
                .paymentStatus(Order.PaymentStatus.PENDING)
                .build();

        orderItem.setOrder(order);
        return order;
    }
}
