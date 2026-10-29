import { useMemo, useState } from 'react'
import { Link, Navigate, useLocation, useNavigate, useParams } from 'react-router-dom'
import { Button, EmptyState, ErrorMessage, Spinner } from '../components/ui'
import { useAuth } from '../context/AuthContext'
import { useAsync } from '../hooks/useAsync'
import { getTimeSlots, getTurf } from '../services/turfs'
import { formatDate, formatMoney, formatTime, hasStarted } from '../utils/format'

const MAX_SLOTS = 10 // CreateBookingRequestDTO: @Size(max = 10)

export default function Slots() {
  const { id } = useParams()
  const { role, isAuthenticated } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const turfQ = useAsync((s) => getTurf(id, s), [id])
  const slotsQ = useAsync((s) => (isAuthenticated ? getTimeSlots(id, undefined, s) : Promise.resolve(null)), [id, isAuthenticated])
  const [date, setDate] = useState(null)
  const [selected, setSelected] = useState([]) // slot objects; all share one court and one date
  const [notice, setNotice] = useState('')

  const slots = useMemo(() => slotsQ.data || [], [slotsQ.data])
  const dates = useMemo(() => [...new Set(slots.map((s) => s.slotDate))].sort(), [slots])
  const activeDate = date && dates.includes(date) ? date : dates[0]
  const byCourt = useMemo(() => {
    const m = new Map()
    slots.filter((s) => s.slotDate === activeDate).forEach((s) => m.set(s.courtNumber, [...(m.get(s.courtNumber) || []), s]))
    return [...m.entries()].sort((a, b) => a[0] - b[0]).map(([court, list]) => [court, list.sort((a, b) => a.startTime.localeCompare(b.startTime))])
  }, [slots, activeDate])

  if (!isAuthenticated) return <Navigate to="/login" replace state={{ from: location.pathname }} />
  if (role !== 'CUSTOMER') return <EmptyState title="Customers only">Only customer accounts can book time slots.</EmptyState>
  if (turfQ.loading || (slotsQ.loading && !slotsQ.data)) return <Spinner label="Loading time slots…" />
  if (turfQ.error) return <ErrorMessage error={turfQ.error} onRetry={turfQ.reload} />
  const turf = turfQ.data

  const toggle = (slot) => {
    setNotice('')
    const already = selected.some((s) => s.timeSlotId === slot.timeSlotId)
    if (already) return setSelected(selected.filter((s) => s.timeSlotId !== slot.timeSlotId))
    // The booking API takes slots of ONE court on ONE date: starting on another court/date starts a new selection.
    const same = selected.length === 0 || (selected[0].courtNumber === slot.courtNumber && selected[0].slotDate === slot.slotDate)
    if (!same) {
      setNotice(`A booking covers one court on one day, so your selection was restarted on Court ${slot.courtNumber}.`)
      return setSelected([slot])
    }
    if (selected.length >= MAX_SLOTS) return setNotice(`You can book at most ${MAX_SLOTS} slots at a time.`)
    setSelected([...selected, slot])
  }

  const total = selected.reduce((sum, s) => sum + Number(s.price), 0)
  const sorted = [...selected].sort((a, b) => a.startTime.localeCompare(b.startTime))
  const noSlots = slotsQ.error?.status === 404
  const refresh = () => { setSelected([]); slotsQ.reload() }

  return (
    <>
      <p><Link to={`/turfs/${id}`}>← {turf.name}</Link></p>
      <div className="page-head">
        <div><h1>Time slots</h1><p className="muted">{turf.name} · 📍 {turf.location}</p></div>
        <Button variant="secondary" onClick={refresh} loading={slotsQ.loading}>Refresh availability</Button>
      </div>

      {noSlots && <EmptyState title="No time slots yet">The owner has not published any upcoming slots for this turf.</EmptyState>}
      {slotsQ.error && !noSlots && <ErrorMessage error={slotsQ.error} onRetry={slotsQ.reload} title="Could not load time slots" />}

      {slots.length > 0 && (
        <>
          <div className="date-tabs" role="group" aria-label="Choose a date">
            {dates.map((d) => (
              <button key={d} className="btn btn-secondary" aria-pressed={d === activeDate} onClick={() => setDate(d)}>{formatDate(d)}</button>
            ))}
          </div>
          <div className="legend" aria-label="Legend">
            <span><i style={{ background: 'var(--surface)' }} />Available</span>
            <span><i style={{ background: 'var(--primary)' }} />Selected</span>
            <span><i style={{ background: 'var(--bg)' }} />Unavailable (reserved, booked or blocked)</span>
            <span><i style={{ background: 'var(--bg)', textDecoration: 'line-through' }} />Past</span>
          </div>
          {notice && <div className="alert alert-info" role="status">{notice}</div>}
          {byCourt.map(([court, list]) => {
            const info = turf.courts?.find((c) => c.courtNumber === court)
            return (
              <section className="panel" key={court} aria-labelledby={`court-${court}`}>
                <h2 id={`court-${court}`}>Court {court}{info?.sports?.length ? <span className="muted small"> · {info.sports.map((s) => s.sportName).join(', ')}</span> : null}</h2>
                <div className="slot-grid">
                  {list.map((s) => {
                    const past = hasStarted(s.slotDate, s.startTime)
                    const free = s.status === 'FREE' && !past
                    const isSel = selected.some((x) => x.timeSlotId === s.timeSlotId)
                    return (
                      <button key={s.timeSlotId} className={`slot ${isSel ? 'selected' : free ? 'free' : ''}`} disabled={!free} aria-pressed={free ? isSel : undefined}
                        title={free ? undefined : past ? 'This slot has already started' : 'This slot is not available'} onClick={() => toggle(s)}>
                        {formatTime(s.startTime)} – {formatTime(s.endTime)}
                        <small>{free ? formatMoney(s.price) : past ? 'Past' : 'Unavailable'}</small>
                      </button>
                    )
                  })}
                </div>
              </section>
            )
          })}
          <div className="sticky-bar">
            <div>
              {selected.length === 0 ? <span className="muted">Select one or more available slots on a single court.</span> : (
                <span className="sw-sel"><strong>{selected.length}</strong> slot{selected.length > 1 ? 's' : ''} · Court {selected[0].courtNumber} · {formatDate(selected[0].slotDate)} · {formatTime(sorted[0].startTime)}–{formatTime(sorted.at(-1).endTime)} · <strong>{formatMoney(total)}</strong></span>
              )}
            </div>
            <Button className="btn-lg" disabled={selected.length === 0}
              onClick={() => navigate('/book', { state: { turf: { turfId: turf.turfId, name: turf.name, location: turf.location }, slots: sorted } })}>
              Continue to Booking
            </Button>
          </div>
        </>
      )}
    </>
  )
}
