# ADR-006: 서비스 메쉬로 Istio 채택 (East-West mTLS + Traffic Management)

- **상태**: 미채택 (Rejected) — 실제 배포는 Vercel + Render(단일 컨테이너 서비스)로,
  K8s 클러스터 자체가 존재하지 않아 Istio를 적용할 대상이 없음
- **날짜**: 2026-03-17
- **결정자**: 인프라 팀

> **현재 상태 참고:** 실제 배포 환경(Vercel + Render 무료 플랜)에서는 Istio 대신
> Resilience4j Circuit Breaker + Spring Cloud Gateway Rate Limiting으로 동일 목표를 달성.
> 이 문서에 설계된 `k8s/base/istio-mesh-config.yml` 등 K8s 매니페스트는 실제로
> 프로비저닝된 적 없는 설계 문서로, 레포 정리 과정에서 함께 삭제됨. 향후 실제 K8s
> 클러스터로 전환할 경우에만 재검토 대상.

## 배경 (Context)

MSA 환경에서 서비스 간(East-West) 트래픽에 대한 보안 및 신뢰성 문제가 발생한다:

1. **서비스 간 통신 평문 전송**: TLS 없이 클러스터 내부 통신이 이뤄지면 Pod 탈취 시 내부 트래픽 도청 가능
2. **인증/인가 중복 구현**: 각 서비스가 개별적으로 JWT 검증 로직을 구현하면 불일치 및 취약점 발생 가능
3. **Circuit Breaker 분산**: Resilience4j를 각 서비스 코드에 직접 구현하면 정책 일관성 유지가 어려움
4. **Observability 단절**: HTTP 레벨 메트릭(에러율, latency P99)을 서비스 코드 변경 없이 수집할 방법 부재

금융·이커머스 도메인에서는 서비스 간 통신도 규제 컴플라이언스(PCI-DSS, 금융보안원 가이드) 대상임.

## 검토한 대안

| 대안 | 장점 | 단점 |
|------|------|------|
| 서비스별 Resilience4j + mTLS 직접 구현 | 의존성 없음 | 코드 복잡도 증가, 정책 불일치 위험 |
| Linkerd | 경량, Rust 기반 저오버헤드 | Envoy 생태계 부재, 트래픽 관리 기능 제한 |
| **Istio (채택)** | 업계 표준, 풍부한 트래픽 관리 | 러닝커브, 컨트롤 플레인 오버헤드 (~2 vCPU) |
| Consul Connect | HashiCorp 생태계 통합 | K8s 네이티브 통합 상대적으로 약함 |

## 결정 (Decision)

**Istio**를 채택하고 `PeerAuthentication: STRICT` 모드로 네임스페이스 전체 mTLS를 강제한다.

## 구현 내용 (`k8s/base/istio-mesh-config.yml`)

```
PeerAuthentication (STRICT)
  └── 모든 서비스 간 통신: mTLS 자동 인증서 발급/교체
      └── SPIFFE ID 기반 서비스 신원 검증

AuthorizationPolicy
  └── payment-service: order-service + api-gateway만 접근 허용
  └── user-service: api-gateway + order-service만 접근 허용

DestinationRule
  └── order-service:  consecutiveErrors=5, ejectTime=30s
  └── payment-service: consecutiveErrors=3, ejectTime=60s (더 엄격)

VirtualService
  └── 결제 API: timeout=10s, retry=3회
  └── 주문 API: timeout=15s, retry=2회
```

## 결과 (Consequences)

**긍정적 효과:**
- 서비스 간 통신 전구간 암호화 (TLS 1.3)
- 인증서 자동 교체 (60일 주기, 코드 변경 없음)
- `istio-proxy` 사이드카를 통한 서비스 코드 무수정 Circuit Breaker
- Prometheus에 `istio_requests_total`, `istio_request_duration_milliseconds` 메트릭 자동 수집

**트레이드오프:**
- 컨트롤 플레인(istiod) 메모리: ~256Mi 추가 필요
- 사이드카 주입으로 Pod 기동 시간 ~3초 증가
- 팀 내 Istio 학습 비용 (약 1주)

## 참고

- [Istio Security Best Practices](https://istio.io/latest/docs/ops/best-practices/security/)
- [PCI-DSS 서비스 간 통신 암호화 요건 6.2.4](https://listings.pcisecuritystandards.org/documents/PCI-DSS-v4_0.pdf)
