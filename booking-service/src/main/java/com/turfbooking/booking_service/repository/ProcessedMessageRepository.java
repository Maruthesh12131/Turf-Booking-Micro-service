package com.turfbooking.booking_service.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.turfbooking.booking_service.entity.ProcessedMessage;

public interface ProcessedMessageRepository extends JpaRepository<ProcessedMessage, String> {
}
