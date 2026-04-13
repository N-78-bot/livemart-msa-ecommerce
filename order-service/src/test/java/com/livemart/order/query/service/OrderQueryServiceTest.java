package com.livemart.order.query.service;

import com.livemart.order.domain.Order;
import com.livemart.order.domain.OrderStatus;
import com.livemart.order.query.dto.OrderStatisticsResponse;
import com.livemart.order.repository.OrderRepository;
import com.livemart.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderQueryService 단위 테스트")
class OrderQueryServiceTest {

    @Mock OrderRepository orderRepository;
    @InjectMocks OrderQueryService orderQueryService;

    @Test
    @DisplayName("getOrderStatistics: findAll() 없이 COUNT/SUM DB 쿼리만 사용")
    void getOrderStatistics_usesDatabaseAggregation() {
        // given — DB 집계 쿼리 모킹 (findAll() 미사용 검증)
        given(orderRepository.countByStatus(OrderStatus.PENDING)).willReturn(10L);
        given(orderRepository.countByStatus(OrderStatus.CONFIRMED)).willReturn(5L);
        given(orderRepository.countByStatus(OrderStatus.SHIPPED)).willReturn(3L);
        given(orderRepository.countByStatus(OrderStatus.DELIVERED)).willReturn(20L);
        given(orderRepository.countByStatus(OrderStatus.CANCELLED)).willReturn(2L);
        given(orderRepository.sumTotalAmountExcludingStatus(OrderStatus.CANCELLED))
                .willReturn(new BigDecimal("1500000"));

        // when
        OrderStatisticsResponse stats = orderQueryService.getOrderStatistics();

        // then
        assertThat(stats.getTotalOrders()).isEqualTo(40L);
        assertThat(stats.getCancelledOrders()).isEqualTo(2L);
        assertThat(stats.getTotalRevenue()).isEqualByComparingTo("1500000");
        assertThat(stats.getAverageOrderAmount())
                .isEqualByComparingTo(new BigDecimal("1500000").divide(BigDecimal.valueOf(38), 2, java.math.RoundingMode.HALF_UP));

        // findAll() 절대 호출되지 않아야 함 (OOM 방지)
        then(orderRepository).should(never()).findAll();
    }

    @Test
    @DisplayName("getOrderStatistics: 주문이 없으면 평균 0 반환")
    void getOrderStatistics_noOrders_returnsZeroAverage() {
        given(orderRepository.countByStatus(any())).willReturn(0L);
        given(orderRepository.sumTotalAmountExcludingStatus(OrderStatus.CANCELLED))
                .willReturn(BigDecimal.ZERO);

        OrderStatisticsResponse stats = orderQueryService.getOrderStatistics();

        assertThat(stats.getTotalOrders()).isZero();
        assertThat(stats.getAverageOrderAmount()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("getOrderDetail: 존재하지 않는 주문 조회 시 404 BusinessException")
    void getOrderDetail_notFound_throws404() {
        given(orderRepository.findByIdWithItems(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> orderQueryService.getOrderDetail(999L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getStatus()).isEqualTo(404));
    }

    @Test
    @DisplayName("getOrderByNumber: 존재하지 않는 주문번호 조회 시 404 BusinessException")
    void getOrderByNumber_notFound_throws404() {
        given(orderRepository.findByOrderNumberWithItems("INVALID")).willReturn(Optional.empty());

        assertThatThrownBy(() -> orderQueryService.getOrderByNumber("INVALID"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getStatus()).isEqualTo(404));
    }
}
