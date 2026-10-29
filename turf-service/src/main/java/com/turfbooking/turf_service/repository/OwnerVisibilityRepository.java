package com.turfbooking.turf_service.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.turfbooking.turf_service.entity.OwnerVisibility;

public interface OwnerVisibilityRepository extends JpaRepository<OwnerVisibility, Long> {
}
