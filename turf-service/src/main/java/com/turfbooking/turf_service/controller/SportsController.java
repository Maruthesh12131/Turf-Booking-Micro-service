package com.turfbooking.turf_service.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.turfbooking.turf_service.dto.SportResponseDTO;
import com.turfbooking.turf_service.service.TurfService;

@RestController
@RequestMapping("/sports")
public class SportsController {

    private final TurfService turfService;

    public SportsController(TurfService turfService) {
        this.turfService = turfService;
    }

    // Public, like browsing turfs: lets a form offer the sports already in use.
    @GetMapping
    public ResponseEntity<List<SportResponseDTO>> listSports() {
        return ResponseEntity.ok(turfService.listSports());
    }
}
