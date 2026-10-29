import { API_BASE_URL } from '../config'

/** Normalised error for every failed request. fieldErrors comes from the services' {errors: {field: msg}} bodies. */
export class ApiError extends Error {
  constructor(status, message, { fieldErrors = {}, body = null } = {}) {
    super(message)
    this.status = status
    this.fieldErrors = fieldErrors
    this.body = body
  }
}

let tokenGetter = () => null
let unauthorizedHandler = () => {}

/** Called once by AuthProvider so the client can attach the token and react to an expired one. */
export function configureHttp({ getToken, onUnauthorized }) {
  tokenGetter = getToken
  unauthorizedHandler = onUnauthorized
}

const FALLBACK = {
  0: 'Cannot reach the server. Check your connection and try again.',
  401: 'Your session has expired or is invalid. Please log in again.',
  403: 'You are not allowed to do this.',
  404: 'Not found.',
  503: 'The service is temporarily unavailable. Please try again shortly.',
  504: 'The service took too long to respond. Please try again.',
}

async function parse(res) {
  const text = await res.text()
  if (!text) return null
  try {
    return JSON.parse(text)
  } catch {
    return text
  }
}

/**
 * request(path, {method, body, headers, auth, signal, expectText})
 * auth: true (default) sends the bearer token when there is one. Never logs tokens or bodies.
 * Resolves to {status, data, headers}.
 */
export async function request(path, { method = 'GET', body, headers = {}, auth = true, signal, expectText = false } = {}) {
  const token = auth ? tokenGetter() : null
  const h = { Accept: expectText ? 'text/plain, application/json' : 'application/json', ...headers }
  if (body !== undefined) h['Content-Type'] = 'application/json'
  if (token) h.Authorization = `Bearer ${token}`

  let res
  try {
    res = await fetch(`${API_BASE_URL}${path}`, {
      method,
      headers: h,
      body: body === undefined ? undefined : JSON.stringify(body),
      signal,
    })
  } catch (e) {
    if (e.name === 'AbortError') throw e
    throw new ApiError(0, FALLBACK[0])
  }

  const data = await parse(res)
  if (res.ok) return { status: res.status, data, headers: res.headers }

  const fieldErrors = data && typeof data === 'object' && data.errors && typeof data.errors === 'object' ? data.errors : {}
  let message = data && typeof data === 'object' && typeof data.message === 'string' ? data.message : null
  if (!message && Object.keys(fieldErrors).length) message = Object.values(fieldErrors).join('. ')
  if (!message) message = FALLBACK[res.status] || `Request failed (${res.status}).`

  // Only a token we actually sent can have "expired"; this keeps a failed login from triggering a logout/redirect loop.
  if (res.status === 401 && token) unauthorizedHandler()
  throw new ApiError(res.status, message, { fieldErrors, body: data })
}
