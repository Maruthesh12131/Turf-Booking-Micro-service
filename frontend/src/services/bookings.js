import { request } from './http'

/**
 * POST /bookings {slotIds} with the required Idempotency-Key header.
 * 201 created, 200 replay of the same key, 202 still processing. 400/409 (rejected) arrive as ApiError whose body has bookingId.
 */
export const createBooking = (slotIds, idempotencyKey) =>
  request('/bookings', { method: 'POST', body: { slotIds }, headers: { 'Idempotency-Key': idempotencyKey } })
    .then((r) => ({ status: r.status, booking: r.data }))

export const getBooking = (id, signal) => request(`/bookings/${id}`, { signal }).then((r) => r.data)
export const myBookings = ({ page = 0, size = 10 } = {}, signal) =>
  request(`/bookings?page=${page}&size=${size}`, { signal }).then((r) => r.data)

/** PUT /bookings/{id}; cancelSlotIds omitted/empty cancels the whole booking. 200 done, 202 release still pending. */
export const cancelBooking = (id, cancelSlotIds) =>
  request(`/bookings/${id}`, { method: 'PUT', body: cancelSlotIds?.length ? { cancelSlotIds } : {} })
    .then((r) => ({ status: r.status, booking: r.data }))

// GET /bookings/owner?turfId=&date=&page=&size= (OWNER)
export function ownerBookings({ turfId, date, page = 0, size = 10 } = {}, signal) {
  const q = new URLSearchParams({ page, size })
  if (turfId) q.set('turfId', turfId)
  if (date) q.set('date', date)
  return request(`/bookings/owner?${q}`, { signal }).then((r) => r.data)
}
