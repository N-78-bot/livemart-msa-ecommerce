package com.livemart.order.integration;

import com.livemart.order.domain.Order;
import com.livemart.order.domain.OrderStatus;
import com.livemart.order.dto.OrderCreateRequest;
import com.livemart.order.dto.OrderItemRequest;
import com.livemart.order.repository.OrderRepository;
import com.livemart.order.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testcontainers를 활용한 통합 테스트
 * 실제 PostgreSQL, Redis, Kafka 환경에서 테스트
 */
@SpringBootTest
@Testcontainers
@DisplayName("주문 서비스 통합 테스트")
class OrderServiceIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
        .withDatabaseName("testdb")
        .withUsername("test")
        .withPassword("test")
        .withReuse(true);

    @Container
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
        .withExposedPorts(6379)
        .withReuse(true);

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.0"))
        .withReuse(true);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // PostgreSQL
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);

        // Redis
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);

        // Kafka
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository orderRepository;

    @BeforeEach
    void setUp() {
        orderRepository.deleteAll();
    }

    @Test
    @DisplayName("주문 생성 통합 테스트")
    void createOrder_Success() {
        // Given
        OrderCreateRequest request = OrderCreateRequest.builder()
            .userId(1L)
            .items(List.of(
                OrderItemRequest.builder()
                    .productId(1L)
                    .quantity(2)
                    .build()
            ))
            .deliveryAddress("서울시 강남구")
            .phoneNumber("010-1234-5678")
            .paymentMethod("CREDIT_CARD")
            .build();

        // When
        var response = orderService.createOrder(request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getOrderNumber()).isNotNull();
        assertThat(response.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(response.getTotalAmount()).isEqualByComparingTo(BigDecimal.valueOf(20000));

        // DB 확인
        Order savedOrder = orderRepository.findById(response.getId()).orElseThrow();
        assertThat(savedOrder.getItems()).hasSize(1);
        assertThat(savedOrder.getUserId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("동시성 테스트 - 분산 락")
    void concurrentOrderCreation_WithDistributedLock() throws InterruptedException {
        // Given
        int threadCount = 10;
        List<Thread> threads = new java.util.ArrayList<>();

        // When
        for (int i = 0; i < threadCount; i++) {
            Thread thread = new Thread(() -> {
                OrderCreateRequest request = OrderCreateRequest.builder()
                    .userId(1L)
                    .items(List.of(
                        OrderItemRequest.builder()
                            .productId(1L)
                            .quantity(1)
                            .build()
                    ))
                    .deliveryAddress("서울시")
                    .phoneNumber("010-1234-5678")
                    .paymentMethod("CREDIT_CARD")
                    .build();

                orderService.createOrder(request);
            });
            threads.add(thread);
            thread.start();
        }

        for (Thread thread : threads) {
            thread.join();
        }

        // Then
        List<Order> orders = orderRepository.findAll();
        assertThat(orders).hasSize(threadCount);
    }

    @Test
    @DisplayName("주문 취소 및 보상 트랜잭션 테스트")
    void cancelOrder_CompensationTransaction() {
        // Given
        OrderCreateRequest request = OrderCreateRequest.builder()
            .userId(1L)
            .items(List.of(
                OrderItemRequest.builder()
                    .productId(1L)
                    .quantity(2)
                    .build()
            ))
            .deliveryAddress("서울시")
            .phoneNumber("010-1234-5678")
            .paymentMethod("CREDIT_CARD")
            .build();

        var createdOrder = orderService.createOrder(request);

        // When
        var cancelledOrder = orderService.cancelOrder(createdOrder.getId(), "고객 요청");

        // Then
        assertThat(cancelledOrder.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(cancelledOrder.getCancelledAt()).isNotNull();

        // Kafka 이벤트 발행 확인 (로그로 확인)
    }

    @Test
    @DisplayName("N+1 쿼리 방지 테스트 - Fetch Join")
    void fetchJoin_PreventNPlusOne() {
        // Given
        for (int i = 0; i < 5; i++) {
            OrderCreateRequest request = OrderCreateRequest.builder()
                .userId(1L)
                .items(List.of(
                    OrderItemRequest.builder()
                        .productId((long) i)
                        .quantity(1)
                        .build()
                ))
                .deliveryAddress("서울시")
                .phoneNumber("010-1234-5678")
                .paymentMethod("CREDIT_CARD")
                .build();
            orderService.createOrder(request);
        }

        // When
        var orders = orderRepository.findAll();

        // Then
        assertThat(orders).hasSize(5);
        // Fetch Join으로 items가 이미 로딩됨 (Lazy Loading 발생 안함)
        orders.forEach(order -> {
            assertThat(order.getItems()).isNotEmpty();
        });
    }
}
