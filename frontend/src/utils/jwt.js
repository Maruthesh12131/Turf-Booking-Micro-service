/**
 * Reads the payload of a JWT WITHOUT verifying it (the browser cannot; the services verify the signature).
 * Used only to know when the token expires and which role to show; every request is still authorised server-side.
 */
export function decodeJwt(token) {
  try {
    const part = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')
    const json = decodeURIComponent(
      atob(part).split('').map((c) => '%' + c.charCodeAt(0).toString(16).padStart(2, '0')).join(''),
    )
    return JSON.parse(json)
  } catch {
    return null
  }
}

export const roleFromClaims = (claims) => (claims?.role ? String(claims.role).replace(/^ROLE_/, '') : null)
export const isExpired = (claims, skewSeconds = 5) => !claims?.exp || claims.exp * 1000 <= Date.now() + skewSeconds * 1000
