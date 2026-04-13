package com.livemart.notification.controller;

import com.livemart.notification.dto.NotificationResponse;
import com.livemart.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * 알림 컨트롤러 (WebFlux 리액티브 엔드포인트)
 */
@Tag(name = "Notification API", description = "실시간 알림 API (WebFlux SSE)")
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @Operation(summary = "사용자 알림 목록", description = "사용자의 최근 알림 목록을 조회합니다")
    @GetMapping("/user/{userId}")
    public Flux<NotificationResponse> getUserNotifications(
            @PathVariable Long userId,
            @RequestHeader(value = "X-User-Id", required = false) String requestingUserId) {
        validateUserAccess(userId, requestingUserId);
        return notificationService.getUserNotifications(userId);
    }

    @Operation(summary = "읽지 않은 알림 수", description = "읽지 않은 알림 수를 반환합니다")
    @GetMapping("/user/{userId}/unread-count")
    public Mono<Long> getUnreadCount(
            @PathVariable Long userId,
            @RequestHeader(value = "X-User-Id", required = false) String requestingUserId) {
        validateUserAccess(userId, requestingUserId);
        return notificationService.getUnreadCount(userId);
    }

    @Operation(summary = "실시간 알림 스트림 (SSE)", description = "Server-Sent Events로 실시간 알림을 수신합니다")
    @GetMapping(value = "/stream/{userId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<NotificationResponse>> streamNotifications(
            @PathVariable Long userId,
            @RequestHeader(value = "X-User-Id", required = false) String requestingUserId) {
        // 즉시 연결 확인 이벤트 전송 → Tomcat에서도 HTTP 헤더가 즉시 flush됨
        ServerSentEvent<NotificationResponse> connected = ServerSentEvent
                .<NotificationResponse>builder().comment("connected").build();

        // 30초마다 heartbeat → 프록시/게이트웨이 연결 유지
        Flux<ServerSentEvent<NotificationResponse>> heartbeats = Flux
                .interval(Duration.ofSeconds(30))
                .map(i -> ServerSentEvent.<NotificationResponse>builder().comment("heartbeat").build());

        Flux<ServerSentEvent<NotificationResponse>> notifications = notificationService
                .streamNotifications(userId)
                .map(notification -> ServerSentEvent.<NotificationResponse>builder()
                        .id(notification.getId())
                        .event("notification")
                        .data(notification)
                        .build());

        return Flux.concat(
                Flux.just(connected),
                Flux.merge(notifications, heartbeats)
        );
    }

    @Operation(summary = "알림 읽음 처리", description = "특정 알림을 읽음 처리합니다")
    @PutMapping("/user/{userId}/{notificationId}/read")
    public Mono<Void> markAsRead(
            @PathVariable Long userId,
            @PathVariable String notificationId,
            @RequestHeader(value = "X-User-Id", required = false) String requestingUserId) {
        validateUserAccess(userId, requestingUserId);
        return notificationService.markAsRead(userId, notificationId);
    }

    @Operation(summary = "전체 알림 읽음 처리", description = "사용자의 모든 알림을 읽음 처리합니다")
    @PutMapping("/user/{userId}/read-all")
    public Mono<Void> markAllAsRead(
            @PathVariable Long userId,
            @RequestHeader(value = "X-User-Id", required = false) String requestingUserId) {
        validateUserAccess(userId, requestingUserId);
        return notificationService.markAllAsRead(userId);
    }

    /** API Gateway가 주입한 X-User-Id 헤더가 경로의 userId와 일치하는지 검증 */
    private void validateUserAccess(Long userId, String requestingUserId) {
        if (requestingUserId != null && !requestingUserId.equals(userId.toString())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "다른 사용자의 알림에 접근할 수 없습니다");
        }
    }

    @Operation(summary = "헬스체크")
    @GetMapping("/health")
    public Mono<String> health() {
        return Mono.just("Notification Service is running (WebFlux)");
    }
}
