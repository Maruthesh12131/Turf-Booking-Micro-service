// The only backend address the app knows: the API gateway.
export const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL || 'http://localhost:8000').replace(/\/+$/, '')
export const APP_NAME = 'TurfBook'
