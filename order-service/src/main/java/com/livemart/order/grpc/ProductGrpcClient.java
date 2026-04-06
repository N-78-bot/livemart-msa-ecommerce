package com.livemart.order.grpc;

import io.grpc.Channel;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * gRPC 클라이언트 - Product Service와의 고성능 바이너리 통신
 *
 * 포트폴리오 목적으로 gRPC 클라이언트 패턴을 수동 구현합니다.
 * 실제 운영에서는 proto 파일에서 자동 생성된 스텁을 사용합니다.
 */
@Slf4j
@Component
public class ProductGrpcClient {

    @Value("${grpc.client.product-grpc-server.address:static://localhost:9095}")
    private String serverAddress;

    private ManagedChannel channel;

    @PostConstruct
    public void init() {
        String host = "localhost";
        int port = 9095;

        try {
            String address = serverAddress.replace("static://", "");
            String[] parts = address.split(":");
            host = parts[0];
            port = Integer.parseInt(parts[1]);
        } catch (Exception e) {
            log.warn("gRPC 서버 주소 파싱 실패, 기본값 사용: localhost:9095");
        }

        try {
            channel = ManagedChannelBuilder.forAddress(host, port)
                    .usePlaintext()
                    .keepAliveTime(30, TimeUnit.SECONDS)
                    .keepAliveTimeout(10, TimeUnit.SECONDS)
                    .maxInboundMessageSize(10 * 1024 * 1024) // 10MB
                    .build();
            log.info("gRPC 채널 초기화 완료: {}:{}", host, port);
        } catch (Exception e) {
            log.warn("gRPC 채널 초기화 실패 (서비스 계속 시작): {}", e.getMessage());
        }
    }

    @PreDestroy
    public void shutdown() {
        if (channel != null && !channel.isShutdown()) {
            try {
                channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
                log.info("gRPC 채널 종료 완료");
            } catch (InterruptedException e) {
                channel.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * gRPC로 상품 정보 조회
     */
    public Optional<ProductGrpcResponse> getProduct(Long productId) {
        log.info("gRPC 상품 조회 요청: productId={}", productId);
        try {
            // 실제 구현에서는 자동 생성된 stub 사용
            // ProductServiceGrpc.ProductServiceBlockingStub stub =
            //     ProductServiceGrpc.newBlockingStub(channel);
            // ProductResponse response = stub.getProduct(ProductRequest.newBuilder().setId(productId).build());

            // 포트폴리오용 시뮬레이션: 채널 상태 확인
            if (channel.isShutdown() || channel.isTerminated()) {
                log.error("gRPC 채널이 종료 상태입니다");
                return Optional.empty();
            }

            log.info("gRPC 상품 조회 완료 (stub 시뮬레이션): productId={}", productId);
            return Optional.empty(); // 실제 stub 사용 시 응답 반환
        } catch (StatusRuntimeException e) {
            log.error("gRPC 상품 조회 실패: status={}, message={}", e.getStatus(), e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * gRPC로 재고 확인
     */
    public StockCheckResponse checkStock(Long productId, int requiredQuantity) {
        log.info("gRPC 재고 확인 요청: productId={}, quantity={}", productId, requiredQuantity);
        try {
            if (channel.isShutdown() || channel.isTerminated()) {
                return new StockCheckResponse(false, 0, "gRPC 채널 비활성");
            }

            // 실제: stub.checkStock(request)
            log.info("gRPC 재고 확인 완료 (stub 시뮬레이션)");
            return new StockCheckResponse(true, 0, "시뮬레이션 응답");
        } catch (StatusRuntimeException e) {
            log.error("gRPC 재고 확인 실패: {}", e.getStatus());
            return new StockCheckResponse(false, 0, e.getMessage());
        }
    }

    /**
     * gRPC로 재고 차감
     */
    public StockDeductResponse deductStock(Long productId, int quantity) {
        log.info("gRPC 재고 차감 요청: productId={}, quantity={}", productId, quantity);
        try {
            if (channel.isShutdown() || channel.isTerminated()) {
                return new StockDeductResponse(false, 0, "gRPC 채널 비활성");
            }

            // 실제: stub.deductStock(request)
            log.info("gRPC 재고 차감 완료 (stub 시뮬레이션)");
            return new StockDeductResponse(true, 0, "시뮬레이션 응답");
        } catch (StatusRuntimeException e) {
            log.error("gRPC 재고 차감 실패: {}", e.getStatus());
            return new StockDeductResponse(false, 0, e.getMessage());
        }
    }

    /**
     * gRPC 채널 상태 확인
     */
    public boolean isChannelActive() {
        return channel != null && !channel.isShutdown() && !channel.isTerminated();
    }

    public record ProductGrpcResponse(Long id, String name, double price, int stockQuantity, String status) {}
    public record StockCheckResponse(boolean available, int currentStock, String message) {}
    public record StockDeductResponse(boolean success, int remainingStock, String message) {}
}
