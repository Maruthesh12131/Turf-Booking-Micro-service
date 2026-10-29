export const formatTime = (t) => {
  if (!t) return ''
  const [h, m] = t.split(':').map(Number)
  const d = new Date(2000, 0, 1, h, m)
  return d.toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' })
}
export const formatDate = (d) => {
  if (!d) return ''
  const [y, m, day] = d.split('-').map(Number)
  return new Date(y, m - 1, day).toLocaleDateString([], { weekday: 'short', day: 'numeric', month: 'short', year: 'numeric' })
}
export const formatMoney = (n) => (n == null ? '' : new Intl.NumberFormat([], { style: 'currency', currency: 'INR', maximumFractionDigits: 2 }).format(Number(n)))
export const formatDateTime = (iso) => (iso ? new Date(iso).toLocaleString() : '')
export const todayISO = () => {
  const d = new Date()
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}
/** True when a yyyy-MM-dd + HH:mm[:ss] slot has already started on this device's clock. */
export const hasStarted = (date, time) => new Date(`${date}T${time.length === 5 ? time + ':00' : time}`).getTime() <= Date.now()
export const humanize = (s) => (s ? s.replace(/_/g, ' ').toLowerCase().replace(/^\w/, (c) => c.toUpperCase()) : '')
