package com.turfbooking.turf_service.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The last verification decision turf-service has applied for an owner. The version comes from owner-service and lets
 * this service ignore duplicate or out-of-order events; it also tells turf creation whether the owner was just revoked.
 */
@Entity
@Table(name = "owner_visibility")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class OwnerVisibility {

    @Id
    private Long ownerId;

    @Column(nullable = false)
    private String verificationStatus;

    @Column(nullable = false)
    private Long version;
}
