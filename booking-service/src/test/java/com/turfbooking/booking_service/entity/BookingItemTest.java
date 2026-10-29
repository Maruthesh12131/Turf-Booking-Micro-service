package com.turfbooking.booking_service.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.turfbooking.booking_service.enums.BookingItemStatus;

class BookingItemTest {

    @Test
    void requestedItemHoldsItsSlotUntilReleased() {
        BookingItem item = BookingItem.requested(9L);
        assertThat(item.getStatus()).isEqualTo(BookingItemStatus.PENDING);
        assertThat(item.getActiveSlotId()).isEqualTo(9L);
    }

    @Test
    void cancellingFreesTheSlotButKeepsTheHistoryRow() {
        BookingItem item = BookingItem.requested(9L);
        item.release(BookingItemStatus.CANCELLED);
        assertThat(item.getActiveSlotId()).isNull();
        assertThat(item.getSlotId()).isEqualTo(9L);
        assertThat(item.getCancelledAt()).isNotNull();
    }

    @Test
    void rejectedItemFreesTheSlotWithoutACancellationTime() {
        BookingItem item = BookingItem.requested(9L);
        item.release(BookingItemStatus.REJECTED);
        assertThat(item.getActiveSlotId()).isNull();
        assertThat(item.getCancelledAt()).isNull();
    }

    @Test
    void onlyReleasingStatusesAreAccepted() {
        assertThatThrownBy(() -> BookingItem.requested(9L).release(BookingItemStatus.ACTIVE))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
