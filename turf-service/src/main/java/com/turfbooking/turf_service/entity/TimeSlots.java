package com.turfbooking.turf_service.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

import org.hibernate.annotations.Check;

import com.turfbooking.turf_service.enums.Status;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Id;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Column;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.JoinColumn;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "time_slots",
       uniqueConstraints = @UniqueConstraint(columnNames = {"court_id", "slotDate", "startTime"}))
// A slot carries a reservation id exactly while it is HELD or BOOKED, so a slot can never be taken without knowing by whom.
@Check(name = "chk_slot_reservation", constraints = "(status in ('HELD', 'BOOKED')) = (reservation_id is not null)")
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class TimeSlots {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long timeSlotId;

    // Calendar date on which the slot starts (a slot after midnight belongs to the next date).
    @NotNull(message = "slotDate is required")
    @Column(nullable = false)
    private LocalDate slotDate;

    @NotNull(message = "startTime is required")
    @Column(nullable = false)
    private LocalTime startTime;

    @NotNull(message = "endTime is required")
    @Column(nullable = false)
    private LocalTime endTime;

    // The column default lets ddl-auto=update add this NOT NULL column to a table that already has rows.
    @NotNull(message = "status is required")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(255) default 'FREE'")
    private Status status = Status.FREE;

    // Price of this slot, copied from the court when the slot is generated; later court price changes never touch it.
    @NotNull(message = "price is required")
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    // Id of the booking that holds or has booked this slot; null otherwise.
    @Column(name = "reservation_id")
    private Long reservationId;

    @ManyToOne
    @JoinColumn(name = "court_id", nullable = false)
    private Courts court;
    
}
