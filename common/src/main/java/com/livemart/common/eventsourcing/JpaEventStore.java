package com.livemart.common.eventsourcing;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnBean(StoredEventRepository.class)
public class JpaEventStore {

    /** 이벤트가 이 개수에 도달하면 스냅샷을 자동 생성 */
    private static final int SNAPSHOT_THRESHOLD = 50;

    private final StoredEventRepository repository;
    private final AggregateSnapshotRepository snapshotRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void save(String aggregateId, String aggregateType, String eventType,
                     long version, Map<String, Object> payload) {
        if (repository.existsByAggregateIdAndVersion(aggregateId, version)) {
            throw new IllegalStateException(
                    "Optimistic concurrency conflict: aggregate=" + aggregateId + ", version=" + version);
        }

        try {
            StoredEvent event = StoredEvent.builder()
                    .aggregateId(aggregateId)
                    .aggregateType(aggregateType)
                    .eventType(eventType)
                    .version(version)
                    .payload(objectMapper.writeValueAsString(payload))
                    .build();
            repository.save(event);
            log.debug("Event persisted: aggregate={}, type={}, version={}", aggregateId, eventType, version);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to persist event", e);
        }
    }

    @Transactional(readOnly = true)
    public List<StoredEvent> getEvents(String aggregateId) {
        return repository.findByAggregateIdOrderByVersionAsc(aggregateId);
    }

    @Transactional(readOnly = true)
    public List<StoredEvent> getEventsFromVersion(String aggregateId, long fromVersion) {
        return repository.findFromVersion(aggregateId, fromVersion);
    }

    @Transactional(readOnly = true)
    public List<StoredEvent> getEventsUntil(String aggregateId, LocalDateTime until) {
        Instant instant = until.atZone(ZoneId.systemDefault()).toInstant();
        return repository.findUntil(aggregateId, instant);
    }

    public Map<String, Object> deserializePayload(StoredEvent event) {
        try {
            return objectMapper.readValue(event.getPayload(), new TypeReference<>() {});
        } catch (Exception e) {
            throw new IllegalStateException("Failed to deserialize event payload", e);
        }
    }

    // ===== Snapshot Methods =====

    /**
     * 스냅샷 저장
     * @param aggregateId  Aggregate ID
     * @param aggregateType  Aggregate 타입 (e.g., "Order")
     * @param version  현재 버전
     * @param state  직렬화할 상태 객체 (Map 또는 POJO)
     */
    @Transactional
    public void saveSnapshot(String aggregateId, String aggregateType, long version, Object state) {
        try {
            String stateJson = objectMapper.writeValueAsString(state);
            AggregateSnapshot snapshot = AggregateSnapshot.builder()
                    .aggregateId(aggregateId)
                    .aggregateType(aggregateType)
                    .version(version)
                    .stateJson(stateJson)
                    .build();
            snapshotRepository.save(snapshot);
            log.info("Snapshot saved: aggregate={}, type={}, version={}", aggregateId, aggregateType, version);

            // 오래된 스냅샷 정리 (최신 3개만 유지)
            cleanupOldSnapshots(aggregateId, aggregateType, 3);
        } catch (Exception e) {
            log.error("Failed to save snapshot for aggregate={}", aggregateId, e);
        }
    }

    /**
     * 최신 스냅샷 조회
     */
    @Transactional(readOnly = true)
    public Optional<AggregateSnapshot> getLatestSnapshot(String aggregateId, String aggregateType) {
        return snapshotRepository.findLatestSnapshot(aggregateId, aggregateType);
    }

    /**
     * 스냅샷 기반 이벤트 로딩 (성능 최적화)
     * 1. 최신 스냅샷 조회
     * 2. 스냅샷 버전 이후의 이벤트만 조회 (델타 이벤트)
     * 3. 스냅샷이 없으면 전체 이벤트 반환
     *
     * @return SnapshotResult (snapshot optional + delta events)
     */
    @Transactional(readOnly = true)
    public SnapshotResult loadWithSnapshot(String aggregateId, String aggregateType) {
        Optional<AggregateSnapshot> snapshot = snapshotRepository.findLatestSnapshot(aggregateId, aggregateType);

        if (snapshot.isPresent()) {
            long snapshotVersion = snapshot.get().getVersion();
            List<StoredEvent> deltaEvents = repository.findFromVersion(aggregateId, snapshotVersion + 1);
            log.debug("Loading with snapshot: aggregate={}, snapshotVersion={}, deltaEvents={}",
                    aggregateId, snapshotVersion, deltaEvents.size());
            return new SnapshotResult(snapshot, deltaEvents);
        }

        // 스냅샷 없으면 전체 이벤트
        List<StoredEvent> allEvents = repository.findByAggregateIdOrderByVersionAsc(aggregateId);
        log.debug("Loading all events (no snapshot): aggregate={}, eventCount={}", aggregateId, allEvents.size());
        return new SnapshotResult(Optional.empty(), allEvents);
    }

    /**
     * 스냅샷 필요 여부 확인 (SNAPSHOT_THRESHOLD 초과 시 true)
     */
    @Transactional(readOnly = true)
    public boolean shouldCreateSnapshot(String aggregateId, String aggregateType) {
        Optional<AggregateSnapshot> latest = snapshotRepository.findLatestSnapshot(aggregateId, aggregateType);
        long fromVersion = latest.map(AggregateSnapshot::getVersion).orElse(0L);
        long deltaCount = repository.findFromVersion(aggregateId, fromVersion + 1).size();
        return deltaCount >= SNAPSHOT_THRESHOLD;
    }

    /**
     * 스냅샷 역직렬화
     */
    public <T> T deserializeSnapshot(AggregateSnapshot snapshot, Class<T> type) {
        try {
            return objectMapper.readValue(snapshot.getStateJson(), type);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to deserialize snapshot for aggregate=" + snapshot.getAggregateId(), e);
        }
    }

    private void cleanupOldSnapshots(String aggregateId, String aggregateType, int keepCount) {
        List<AggregateSnapshot> all = snapshotRepository.findAllByAggregate(aggregateId, aggregateType);
        if (all.size() > keepCount) {
            List<AggregateSnapshot> toDelete = all.subList(keepCount, all.size());
            snapshotRepository.deleteAll(toDelete);
            log.debug("Cleaned up {} old snapshots for aggregate={}", toDelete.size(), aggregateId);
        }
    }

    // ===== Stats =====

    @Transactional(readOnly = true)
    public EventStoreStats getStats() {
        long totalAggregates = repository.countDistinctAggregates();
        long totalEvents = repository.count();
        long totalSnapshots = snapshotRepository.count();
        double avg = totalAggregates > 0 ? (double) totalEvents / totalAggregates : 0;
        return new EventStoreStats(totalAggregates, totalEvents, totalSnapshots, avg);
    }

    @Transactional(readOnly = true)
    public Map<String, Long> getEventTypeStats() {
        return repository.countByEventType().stream()
                .collect(Collectors.toMap(
                        row -> (String) row[0],
                        row -> (Long) row[1]
                ));
    }

    public record EventStoreStats(long totalAggregates, long totalEvents,
            long totalSnapshots, double avgEventsPerAggregate) {}

    public record SnapshotResult(
            Optional<AggregateSnapshot> snapshot,
            List<StoredEvent> deltaEvents) {
        public boolean hasSnapshot() { return snapshot.isPresent(); }
    }
}
