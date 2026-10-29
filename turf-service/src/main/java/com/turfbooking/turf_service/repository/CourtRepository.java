package com.turfbooking.turf_service.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.turfbooking.turf_service.entity.Courts;

@Repository
public interface CourtRepository extends JpaRepository<Courts, Long> {

    public List<Courts> findByTurf_TurfId(Long turfId);
    
}
