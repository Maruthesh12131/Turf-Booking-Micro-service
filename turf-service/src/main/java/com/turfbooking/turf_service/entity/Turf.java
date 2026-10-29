package com.turfbooking.turf_service.entity;

import java.time.LocalTime;
import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import jakarta.persistence.OneToMany;
import jakarta.persistence.JoinColumn;

import com.turfbooking.turf_service.dto.CourtResponseDTO;
import com.turfbooking.turf_service.enums.TurfStatus;
import com.turfbooking.turf_service.entity.Courts;

@Entity
@Table(name = "turfs")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class Turf {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long turfId;

    @NotBlank(message = "name is required")
    @Column(nullable = false)
    private String name;

    @NotBlank(message = "location is required")
    @Column(nullable = false)
    private String location;

    private String description;

    // Id of the owner profile in owner-service (not a foreign key). Nullable so turfs created before ownership existed still load.
    @Column(name = "owner_id")
    private Long ownerId;

    // HIDDEN turfs are invisible to customers. Only an admin decision on the owner changes this (via owner-service events).
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(255) default 'ACTIVE'")
    private TurfStatus status = TurfStatus.ACTIVE;

    @OneToMany(mappedBy = "turf")
    private List<Courts> courts;

    @Column(nullable = false)
    private LocalTime openingTime;

    @Column(nullable = false)
    private LocalTime closingTime;
}
