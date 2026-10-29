package com.turfbooking.booking_service.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import jakarta.validation.Validation;
import jakarta.validation.Validator;

class CreateBookingRequestDTOTest {

    Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private boolean valid(List<Long> ids) {
        return validator.validate(new CreateBookingRequestDTO(ids)).isEmpty();
    }

    @Test
    void oneToTenSlotsAreAccepted() {
        assertThat(valid(List.of(1L))).isTrue();
        assertThat(valid(List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L))).isTrue();
    }

    @Test
    void emptyMissingElevenOrNullSlotIdsAreRejected() {
        assertThat(valid(null)).isFalse();
        assertThat(valid(List.of())).isFalse();
        assertThat(valid(List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L, 11L))).isFalse();
        assertThat(valid(new ArrayList<>(Arrays.asList(1L, null)))).isFalse();
    }
}
