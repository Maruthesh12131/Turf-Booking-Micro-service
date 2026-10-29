package com.turfbooking.turf_service.entity;

import java.lang.annotation.Inherited;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Id;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Column;
import jakarta.persistence.ManyToMany;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "sports")
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class Sports {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long sportId;

    @NotBlank(message = "sportName is required")
    @Column(nullable = false, unique = true, length = 50)
    private String sportName;

    // Never serialized: courts point at sports and sports at courts, which would loop forever.
    @JsonIgnore
    @ManyToMany(mappedBy = "availableSports")
    private List<Courts> courts;
    
}
