package com.turfbooking.turf_service.dto;

import java.util.List;

import org.springframework.data.domain.Page;

public record PagedResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public static <T> PagedResponse<T> of(Page<?> page, List<T> content) {
        return new PagedResponse<>(content, page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
}
