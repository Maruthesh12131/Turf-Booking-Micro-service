import { request } from './http'

// GET /turfs?location=&page=&size= (public) -> PagedResponse<TurfDetailsDTO>
export function searchTurfs({ location = '', page = 0, size = 12 } = {}, signal) {
  const q = new URLSearchParams({ page, size })
  if (location.trim()) q.set('location', location.trim())
  return request(`/turfs?${q}`, { auth: false, signal }).then((r) => r.data)
}
// GET /turfs/{id} (public) -> TurfDetailsDTO
export const getTurf = (id, signal) => request(`/turfs/${id}`, { auth: false, signal }).then((r) => r.data)
// GET /sports (public) -> [{sportId, sportName}]
export const listSports = (signal) => request('/sports', { auth: false, signal }).then((r) => r.data)
// GET /turfs/{id}/timeslots[?date=] (CUSTOMER, OWNER). Customers get CustomerTimeSlotDTO, owners TimeSlotResponseDTO.
// 404 means no slots have been created for that turf/date.
export function getTimeSlots(id, date, signal) {
  const q = date ? `?date=${date}` : ''
  return request(`/turfs/${id}/timeslots${q}`, { signal }).then((r) => r.data)
}

// ---- OWNER ----
export const myTurfs = (signal) => request('/turfs/mine', { signal }).then((r) => r.data)
export const createTurf = (body) => request('/turfs', { method: 'POST', body }).then((r) => r.data)
export const updateTurf = (id, body) => request(`/turfs/${id}`, { method: 'PUT', body }).then((r) => r.data)
export const generateSlots = (courtNumber, turfId, startDate) =>
  request(`/turfs/courts/${courtNumber}/timeslots`, { method: 'POST', body: { turfId, startDate } }).then((r) => r.data)
export const setSlotStatus = (turfId, slotId, status) =>
  request(`/turfs/${turfId}/timeslots/${slotId}/status`, { method: 'PUT', body: { status } }).then((r) => r.data)
// DELETE /turfs/{id} (OWNER own turf, or ADMIN any) -> 204
export const deleteTurf = (id) => request(`/turfs/${id}`, { method: 'DELETE' })
