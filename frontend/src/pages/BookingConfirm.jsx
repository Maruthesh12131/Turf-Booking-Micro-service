import { useRef, useState } from 'react'
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom'
import { Button, ErrorMessage, Notice } from '../components/ui'
import { useAuth } from '../context/AuthContext'
import { createBooking } from '../services/bookings'
import { formatDate, formatMoney, formatTime } from '../utils/format'

export default function BookingConfirm() {
  const { state } = useLocation()
  const { role } = useAuth()
  const navigate = useNavigate()
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState(null)
  // One key per booking attempt. It is reused only while the outcome is unknown (network/5xx), so a retry cannot
  // double-book; after a definitive answer (rejected) a fresh key is used for the next, different attempt.
  const key = useRef(crypto.randomUUID())
  const inFlight = useRef(false)

  if (!state?.slots?.length || role !== 'CUSTOMER') return <Navigate to="/" replace />
  const { turf, slots } = state
  const total = slots.reduce((s, x) => s + Number(x.price), 0)

  async function confirm() {
    if (inFlight.current) return
    inFlight.current = true
    setBusy(true)
    setError(null)
    try {
      const { booking } = await createBooking(slots.map((s) => s.timeSlotId), key.current)
      // 201/200 = confirmed, 202 = still pending: the booking page shows the real status and polls if needed.
      navigate(`/bookings/${booking.bookingId}`, { replace: true, state: { fresh: true } })
    } catch (e) {
      if (e.status === 400 || e.status === 409 || e.status === 422) key.current = crypto.randomUUID()
      setError(e.status === 409 ? `${e.message} Go back and pick other slots.` : e)
      inFlight.current = false
      setBusy(false)
    }
  }

  return (
    <div className="auth-wrap" style={{ maxWidth: 640 }}>
      <p><Link to={`/turfs/${turf.turfId}/slots`}>← Change slots</Link></p>
      <div className="panel">
        <h1>Review your booking</h1>
        <p className="muted">{turf.name} · 📍 {turf.location}</p>
        <p><strong>Court {slots[0].courtNumber}</strong> · {formatDate(slots[0].slotDate)}</p>
        <ul className="list">
          {slots.map((s) => <li key={s.timeSlotId}><span>{formatTime(s.startTime)} – {formatTime(s.endTime)}</span><span>{formatMoney(s.price)}</span></li>)}
          <li><strong>Total</strong><strong>{formatMoney(total)}</strong></li>
        </ul>
        <Notice>No payment is taken here. Your booking is reserved and the amount is not collected online.</Notice>
        {error && <ErrorMessage error={error} title="Booking was not made" />}
        <Button className="btn-lg" loading={busy} onClick={confirm} style={{ width: '100%' }}>{busy ? 'Reserving…' : 'Confirm booking'}</Button>
      </div>
    </div>
  )
}
