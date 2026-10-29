import { useCallback, useEffect, useRef, useState } from 'react'
import { Link, useLocation, useParams } from 'react-router-dom'
import { Button, ConfirmDialog, EmptyState, ErrorMessage, Notice, Spinner, StatusBadge } from '../components/ui'
import { cancelBooking, getBooking } from '../services/bookings'
import { formatDate, formatDateTime, formatMoney, formatTime } from '../utils/format'

const POLL_MS = 2000
const POLL_MAX = 30 // ~1 minute, then the user can refresh manually

const isSettling = (b) => b.status === 'PENDING_RESERVATION' || b.items.some((i) => i.status === 'RELEASE_PENDING' || i.status === 'PENDING')

const MESSAGES = {
  CONFIRMED: ['success', 'Your booking is confirmed.'],
  PARTIALLY_CANCELLED: ['info', 'Part of this booking was cancelled; the remaining slots stand.'],
  CANCELLED: ['info', 'This booking was cancelled.'],
  REJECTED: ['error', 'The booking could not be made.'],
  PENDING_RESERVATION: ['warn', 'We are reserving your slots. This usually takes a few seconds.'],
}

export default function BookingDetail() {
  const { id } = useParams()
  const { state } = useLocation()
  const [booking, setBooking] = useState(null)
  const [error, setError] = useState(null)
  const [gaveUp, setGaveUp] = useState(false)
  const [cancelOpen, setCancelOpen] = useState(false)
  const [picked, setPicked] = useState([])
  const [busy, setBusy] = useState(false)
  const [cancelError, setCancelError] = useState(null)
  const [reloadKey, setReloadKey] = useState(0)
  const timer = useRef(null)

  // Poll while the backend is still working; stop at a terminal state, after POLL_MAX tries, or on unmount.
  useEffect(() => {
    const ctrl = new AbortController()
    let tries = 0
    setGaveUp(false)
    const tick = async () => {
      try {
        const b = await getBooking(id, ctrl.signal)
        if (ctrl.signal.aborted) return
        setBooking(b)
        setError(null)
        if (isSettling(b)) {
          if (++tries >= POLL_MAX) return setGaveUp(true)
          timer.current = setTimeout(tick, POLL_MS)
        }
      } catch (e) {
        if (e.name !== 'AbortError') setError(e)
      }
    }
    tick()
    return () => { ctrl.abort(); clearTimeout(timer.current) }
  }, [id, reloadKey])

  const refetch = useCallback(() => { clearTimeout(timer.current); setReloadKey((k) => k + 1) }, [])

  if (error && !booking) return error.status === 404 ? <EmptyState title="Booking not found"><Link to="/bookings">Back to my bookings</Link></EmptyState> : <ErrorMessage error={error} onRetry={refetch} />
  if (!booking) return <Spinner label="Loading booking…" />

  const activeItems = booking.items.filter((i) => i.status === 'ACTIVE')
  const canCancel = (booking.status === 'CONFIRMED' || booking.status === 'PARTIALLY_CANCELLED') && activeItems.length > 0
  const [kind, msg] = MESSAGES[booking.status] || ['info', '']
  const allPicked = picked.length === 0

  async function doCancel() {
    setBusy(true)
    setCancelError(null)
    try {
      const { booking: b } = await cancelBooking(booking.bookingId, picked)
      setBooking(b)
      setCancelOpen(false)
      setPicked([])
      refetch() // 202 means the release is still in flight: this resumes polling until it settles
    } catch (e) {
      setCancelError(e.message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <>
      <p><Link to="/bookings">← My bookings</Link></p>
      <div className="page-head">
        <div><h1>Booking #{booking.bookingId}</h1><p className="muted">Made {formatDateTime(booking.createdAt)}</p></div>
        <div className="inline"><StatusBadge status={booking.status} /><StatusBadge status={booking.paymentStatus} /></div>
      </div>
      <Notice kind={kind}>{msg}{booking.status === 'REJECTED' && booking.failureReason ? ` ${booking.failureReason}` : ''}
        {state?.fresh && booking.status === 'PENDING_RESERVATION' && ' Do not book again; this page updates by itself.'}</Notice>
      {isSettling(booking) && !gaveUp && <p className="muted" role="status"><span className="spinner" aria-hidden="true" /> Checking status…</p>}
      {gaveUp && <Notice kind="warn">This is taking longer than usual. <Button variant="secondary" onClick={refetch}>Check again</Button></Notice>}
      {error && <ErrorMessage error={error} title="Could not refresh" />}

      <div className="panel">
        <p><strong>Turf:</strong> <Link to={`/turfs/${booking.turfId}`}>#{booking.turfId}</Link> · <strong>Court</strong> {booking.courtNumber} · {formatDate(booking.bookingDate)}</p>
        <div className="table-wrap">
          <table>
            <thead><tr><th>Time</th><th>Price</th><th>Status</th></tr></thead>
            <tbody>{booking.items.map((i) => (
              <tr key={i.slotId}><td>{formatTime(i.startTime)} – {formatTime(i.endTime)}</td><td>{formatMoney(i.price)}</td><td><StatusBadge status={i.status} /></td></tr>
            ))}</tbody>
          </table>
        </div>
        <p style={{ marginTop: 12 }}><strong>Current amount:</strong> {formatMoney(booking.currentAmount)}
          {booking.currentAmount !== booking.totalAmount && <span className="muted"> (originally {formatMoney(booking.totalAmount)})</span>}
        </p>
        <p className="muted small">Payment is not collected online.</p>
        {canCancel && <Button variant="danger" onClick={() => { setPicked([]); setCancelError(null); setCancelOpen(true) }}>Cancel booking…</Button>}
      </div>

      <ConfirmDialog open={cancelOpen} danger busy={busy} title="Cancel this booking?" confirmLabel={allPicked ? 'Cancel whole booking' : `Cancel ${picked.length} slot(s)`} onConfirm={doCancel} onCancel={() => setCancelOpen(false)}>
        <p>Choose slots to cancel, or leave all unchecked to cancel the entire booking. Slots can only be cancelled until shortly before they start; this cannot be undone.</p>
        {cancelError && <div className="alert alert-error" role="alert">{cancelError}</div>}
        <ul className="list">
          {activeItems.map((i) => (
            <li key={i.slotId}><label><input type="checkbox" checked={picked.includes(i.slotId)} onChange={(e) => setPicked((p) => e.target.checked ? [...p, i.slotId] : p.filter((x) => x !== i.slotId))} /> {formatTime(i.startTime)} – {formatTime(i.endTime)}</label><span>{formatMoney(i.price)}</span></li>
          ))}
        </ul>
      </ConfirmDialog>
    </>
  )
}
