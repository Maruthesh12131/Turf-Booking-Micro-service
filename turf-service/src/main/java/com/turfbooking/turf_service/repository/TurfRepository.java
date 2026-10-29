package com.turfbooking.turf_service.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.turfbooking.turf_service.enums.TurfStatus;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.turfbooking.turf_service.entity.Turf;

@Repository
public interface TurfRepository extends JpaRepository<Turf, Long> {

    List<Turf> findByOwnerId(Long ownerId);

    Page<Turf> findByStatus(TurfStatus status, Pageable pageable);

    // pattern is already lower-cased, trimmed and has LIKE wildcards escaped with a backslash (see TurfService.likePattern).
    @Query("select t from Turf t where t.status = :status and lower(t.location) like :pattern escape '\\'")
    Page<Turf> searchByLocation(@Param("status") TurfStatus status, @Param("pattern") String pattern, Pageable pageable);

    // Bulk update for owner verification changes; returns the number of turfs changed.
    @Modifying
    @Query("update Turf t set t.status = :status where t.ownerId = :ownerId and t.status <> :status")
    int updateStatusByOwner(@Param("ownerId") Long ownerId, @Param("status") TurfStatus status);
}
