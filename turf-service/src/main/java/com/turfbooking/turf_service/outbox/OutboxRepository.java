package com.turfbooking.turf_service.outbox;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;

public interface OutboxRepository extends JpaRepository<OutboxEvent, Long> {

    boolean existsByAggregateIdAndPublishedAtIsNull(String aggregateId);

    // Oldest first, locked so two relay instances never send the same batch (the second waits, then sees it published).
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from OutboxEvent e where e.publishedAt is null order by e.id")
    List<OutboxEvent> findUnpublishedForUpdate(Pageable pageable);
}
