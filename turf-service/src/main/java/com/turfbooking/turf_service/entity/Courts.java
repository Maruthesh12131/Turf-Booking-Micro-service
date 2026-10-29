package com.turfbooking.turf_service.entity;
import java.util.List;

import org.hibernate.annotations.BatchSize;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Column;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.JoinTable;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import com.turfbooking.turf_service.dto.TurfDetailsDTO;

@Entity
@Table(name = "courts")
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class Courts {

    @Id 
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long courtId;

    private Long courtNumber;

    @Positive(message = "amountPerHour must be a positive value")
    @Column(nullable = false)
    private Long amountPerHour;

    private Long length;
    private Long width;

    @ManyToOne
    @JoinColumn(name = "turf_id", nullable = false)
    private Turf turf;

    @OneToMany(mappedBy = "court")
    private List<TimeSlots> timeSlots;

    // In JSON this is "sports": a list of {"sportName": "Football"}. Only the name is used; see TurfService.resolveSports.
    @JsonProperty("sports")
    @JsonAlias("availableSports")
    @BatchSize(size = 50)
    @ManyToMany
    @JoinTable(
        name = "court_sports",
        joinColumns = @JoinColumn(name = "court_id"),
        inverseJoinColumns = @JoinColumn(name = "sport_id")
    )
    private List<Sports> availableSports;
    
}
