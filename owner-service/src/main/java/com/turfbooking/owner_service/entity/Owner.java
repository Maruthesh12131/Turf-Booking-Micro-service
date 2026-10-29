package com.turfbooking.owner_service.entity;

import java.time.Instant;

import com.turfbooking.owner_service.enums.VerificationStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "owners")
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class Owner {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long ownerId;

    // Id of the account in auth-service. Deliberately not a foreign key: that data lives in another service.
    @Column(nullable = false, unique = true)
    private Long userId;

    @Column(nullable = false)
    private String businessName;

    @Column(nullable = false)
    private String phoneNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VerificationStatus verificationStatus = VerificationStatus.PENDING;

    // Set by an admin decision; version counts those decisions so consumers can ignore stale or reordered events.
    @Column(length = 500)
    private String verificationReason;

    // userId of the admin who made the last decision.
    private Long verifiedBy;

    private Instant verificationUpdatedAt;

    @Column(nullable = false, columnDefinition = "bigint default 0")
    private long verificationVersion = 0;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
