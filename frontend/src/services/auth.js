import { request } from './http'

// POST /users/create — SignupDTO {email, name, password, role, businessName?, phoneNumber}; returns UserResponseDTO (no token).
export const signup = (payload) => request('/users/create', { method: 'POST', body: payload, auth: false }).then((r) => r.data)

// POST /users/login — LoginDTO {email, password}; the response body is the raw JWT string (not JSON).
export async function login(email, password) {
  const { data } = await request('/users/login', { method: 'POST', body: { email, password }, auth: false, expectText: true })
  const token = typeof data === 'string' ? data.replace(/^"|"$/g, '').trim() : ''
  if (!token) throw new Error('The server did not return a token.')
  return token
}

// GET /users/{email} — only the caller's own record; used to confirm the token really is accepted and to get the role.
export const getMe = (email) => request(`/users/${encodeURIComponent(email)}`).then((r) => r.data)
