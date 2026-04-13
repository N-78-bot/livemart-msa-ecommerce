package com.livemart.order.service;

import com.livemart.order.domain.Order;
import com.livemart.order.domain.OrderStatus;
import com.livemart.order.domain.ReturnRequest;
import com.livemart.order.dto.ReturnRequestDto;
import com.livemart.order.repository.OrderRepository;
import com.livemart.order.repository.ReturnRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.security.SecureRandom;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReturnService {

    private final ReturnRequestRepository returnRequestRepository;
    private final OrderRepository orderRepository;

    @Transactional
    public ReturnRequestDto.Response createReturn(ReturnRequestDto.Create request) {
        Order order = orderRepository.findById(request.getOrderId())
                .orElseThrow(() -> new IllegalArgumentException("주문을 찾을 수 없습니다"));

        if (!order.getUserId().equals(request.getUserId())) {
            throw new IllegalStateException("본인의 주문만 반품 요청할 수 있습니다");
        }

        if (order.getStatus() != OrderStatus.DELIVERED && order.getStatus() != OrderStatus.CONFIRMED) {
            throw new IllegalStateException("배송 완료 또는 확인된 주문만 반품 가능합니다. 현재 상태: " + order.getStatus());
        }

        ReturnRequest returnRequest = ReturnRequest.builder()
                .returnNumber(generateReturnNumber())
                .order(order)
                .userId(request.getUserId())
                .returnType(request.getReturnType())
                .status(ReturnRequest.ReturnStatus.REQUESTED)
                .reason(request.getReason())
                .reasonDetail(request.getReasonDetail())
                .imageUrls(request.getImageUrls())
                .build();

        returnRequest = returnRequestRepository.save(returnRequest);
        log.info("반품 요청 생성: returnNumber={}, orderId={}", returnRequest.getReturnNumber(), order.getId());
        return ReturnRequestDto.Response.from(returnRequest);
    }

    @Transactional
    public ReturnRequestDto.Response approveReturn(Long returnId, ReturnRequestDto.Approve request) {
        ReturnRequest returnRequest = getReturnById(returnId);

        if (returnRequest.getStatus() != ReturnRequest.ReturnStatus.REQUESTED) {
            throw new IllegalStateException("요청 상태의 반품만 승인할 수 있습니다");
        }

        returnRequest.approve(
                request.getRefundAmount() != null ? request.getRefundAmount() : returnRequest.getOrder().getTotalAmount(),
                request.getAdminNote()
        );

        log.info("반품 승인: returnNumber={}, refundAmount={}", returnRequest.getReturnNumber(), returnRequest.getRefundAmount());
        return ReturnRequestDto.Response.from(returnRequest);
    }

    @Transactional
    public ReturnRequestDto.Response rejectReturn(Long returnId, ReturnRequestDto.Reject request) {
        ReturnRequest returnRequest = getReturnById(returnId);

        if (returnRequest.getStatus() != ReturnRequest.ReturnStatus.REQUESTED) {
            throw new IllegalStateException("요청 상태의 반품만 거절할 수 있습니다");
        }

        returnRequest.reject(request.getAdminNote());
        log.info("반품 거절: returnNumber={}", returnRequest.getReturnNumber());
        return ReturnRequestDto.Response.from(returnRequest);
    }

    @Transactional
    public ReturnRequestDto.Response completeReturn(Long returnId) {
        ReturnRequest returnRequest = getReturnById(returnId);
        returnRequest.complete();

        // 주문 상태도 CANCELLED로 변경 (환불 처리)
        Order order = returnRequest.getOrder();
        order.cancel();

        log.info("반품 완료: returnNumber={}, refundAmount={}", returnRequest.getReturnNumber(), returnRequest.getRefundAmount());
        return ReturnRequestDto.Response.from(returnRequest);
    }

    @Transactional
    public ReturnRequestDto.Response updateReturnStatus(Long returnId, ReturnRequest.ReturnStatus status) {
        ReturnRequest returnRequest = getReturnById(returnId);

        switch (status) {
            case IN_TRANSIT -> returnRequest.markInTransit();
            case RECEIVED -> returnRequest.markReceived();
            case COMPLETED -> returnRequest.complete();
            case CANCELLED -> returnRequest.cancel();
            default -> throw new IllegalArgumentException("지원하지 않는 상태 변경입니다: " + status);
        }

        log.info("반품 상태 변경: returnNumber={}, status={}", returnRequest.getReturnNumber(), status);
        return ReturnRequestDto.Response.from(returnRequest);
    }

    public ReturnRequestDto.Response getReturn(Long returnId) {
        return ReturnRequestDto.Response.from(getReturnById(returnId));
    }

    public ReturnRequestDto.Response getReturnByNumber(String returnNumber) {
        ReturnRequest req = returnRequestRepository.findByReturnNumber(returnNumber)
                .orElseThrow(() -> new IllegalArgumentException("반품 요청을 찾을 수 없습니다"));
        return ReturnRequestDto.Response.from(req);
    }

    public Page<ReturnRequestDto.Response> getUserReturns(Long userId, Pageable pageable) {
        return returnRequestRepository.findByUserIdOrderByRequestedAtDesc(userId, pageable)
                .map(ReturnRequestDto.Response::from);
    }

    public Page<ReturnRequestDto.Response> getReturnsByStatus(ReturnRequest.ReturnStatus status, Pageable pageable) {
        return returnRequestRepository.findByStatusOrderByRequestedAtDesc(status, pageable)
                .map(ReturnRequestDto.Response::from);
    }

    private ReturnRequest getReturnById(Long id) {
        return returnRequestRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("반품 요청을 찾을 수 없습니다"));
    }

    private String generateReturnNumber() {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String random = String.format("%06X", new SecureRandom().nextInt(0xFFFFFF));
        return "RTN-" + timestamp + "-" + random;
    }
}
