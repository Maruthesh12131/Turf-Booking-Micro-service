import { afterEach, describe, expect, it, vi } from 'vitest'
import { configureHttp, request } from '../services/http'
import { decodeJwt, isExpired, roleFromClaims } from '../utils/jwt'

const reply = (status, body) => vi.fn().mockImplementation(async () => new Response(body === undefined ? null : typeof body === 'string' ? body : JSON.stringify(body), { status }))
afterEach(() => vi.unstubAllGlobals())

describe('http client', () => {
  it('attaches the bearer token and Idempotency-Key header', async () => {
    const f = reply(201, { bookingId: 1 })
    vi.stubGlobal('fetch', f)
    configureHttp({ getToken: () => 'tok', onUnauthorized: () => {} })
    await request('/bookings', { method: 'POST', body: { slotIds: [1] }, headers: { 'Idempotency-Key': 'k1' } })
    const [url, init] = f.mock.calls[0]
    expect(url).toMatch(/\/bookings$/)
    expect(init.headers.Authorization).toBe('Bearer tok')
    expect(init.headers['Idempotency-Key']).toBe('k1')
  })

  it('turns field errors into one readable message', async () => {
    vi.stubGlobal('fetch', reply(400, { status: 400, errors: { name: 'name is required', location: 'location is required' } }))
    await expect(request('/turfs', { method: 'POST', body: {} })).rejects.toMatchObject({ status: 400, message: 'name is required. location is required', fieldErrors: { name: 'name is required' } })
  })

  it('signs out on 401 only when a token was sent', async () => {
    const onUnauthorized = vi.fn()
    configureHttp({ getToken: () => 'tok', onUnauthorized })
    vi.stubGlobal('fetch', reply(401))
    await expect(request('/bookings')).rejects.toMatchObject({ status: 401 })
    expect(onUnauthorized).toHaveBeenCalledTimes(1)
    await expect(request('/users/login', { method: 'POST', body: {}, auth: false })).rejects.toMatchObject({ status: 401 })
    expect(onUnauthorized).toHaveBeenCalledTimes(1)
  })

  it('reports an unreachable server', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('failed')))
    await expect(request('/turfs', { auth: false })).rejects.toMatchObject({ status: 0 })
  })

  it('reads a raw-text login response', async () => {
    vi.stubGlobal('fetch', reply(200, 'aaa.bbb.ccc'))
    const { data } = await request('/users/login', { method: 'POST', body: {}, auth: false, expectText: true })
    expect(data).toBe('aaa.bbb.ccc')
  })
})

describe('jwt helpers', () => {
  const make = (p) => `h.${btoa(JSON.stringify(p)).replace(/=+$/, '')}.s`
  it('reads role and expiry', () => {
    const c = decodeJwt(make({ sub: 'a@b.c', role: 'ROLE_OWNER', exp: Math.floor(Date.now() / 1000) + 600 }))
    expect(roleFromClaims(c)).toBe('OWNER')
    expect(isExpired(c)).toBe(false)
    expect(isExpired({ exp: 1 })).toBe(true)
  })
  it('returns null for garbage', () => expect(decodeJwt('nope')).toBeNull())
})
