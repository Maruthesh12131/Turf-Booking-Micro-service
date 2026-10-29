import { request } from './http'

// OWNER: GET/PUT/POST /owners/me, /owners — OwnerRequestDTO {businessName, phoneNumber}
export const getOwnerProfile = (signal) => request('/owners/me', { signal }).then((r) => r.data)
export const updateOwnerProfile = (body) => request('/owners/me', { method: 'PUT', body }).then((r) => r.data)
export const createOwnerProfile = (body) => request('/owners', { method: 'POST', body }).then((r) => r.data)
// ADMIN: PUT /owners/{ownerId}/verification — {status: VERIFIED|REJECTED, reason?}
export const setOwnerVerification = (ownerId, status, reason) =>
  request(`/owners/${ownerId}/verification`, { method: 'PUT', body: reason ? { status, reason } : { status } }).then((r) => r.data)
