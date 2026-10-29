import { useMemo, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { Button, EmptyState, ErrorMessage, Field, Notice, Spinner } from '../../components/ui'
import { useAsync } from '../../hooks/useAsync'
import { generateSlots, getTimeSlots, myTurfs, setSlotStatus } from '../../services/turfs'
import { formatDate, formatMoney, formatTime, hasStarted, todayISO } from '../../utils/format'

export default function OwnerSlots() {
  const { id } = useParams()
  const turfQ = useAsync(async (s) => (await myTurfs(s)).find((t) => String(t.turfId) === id) || null, [id])
  const slotsQ = useAsync((s) => getTimeSlots(id, undefined, s), [id])
  const [court, setCourt] = useState('')
  const [start, setStart] = useState(todayISO())
  const [genBusy, setGenBusy] = useState(false)
  const [genMsg, setGenMsg] = useState(null)
  const [date, setDate] = useState(null)
  const [rowError, setRowError] = useState(null)
  const [pending, setPending] = useState(null)

  const slots = useMemo(() => slotsQ.data || [], [slotsQ.data])
  const dates = useMemo(() => [...new Set(slots.map((s) => s.slotDate))].sort(), [slots])
  const activeDate = date && dates.includes(date) ? date : dates[0]
  const byCourt = useMemo(() => {
    const m = new Map()
    slots.filter((s) => s.slotDate === activeDate).forEach((s) => m.set(s.courtNumber, [...(m.get(s.courtNumber) || []), s]))
    return [...m.entries()].sort((a, b) => a[0] - b[0]).map(([c, l]) => [c, l.sort((a, b) => a.startTime.localeCompare(b.startTime))])
  }, [slots, activeDate])

  if (turfQ.loading) return <Spinner />
  if (!turfQ.data) return <ErrorMessage error={turfQ.error || 'Turf not found among your turfs.'} onRetry={turfQ.reload} />
  const turf = turfQ.data
  const courtNo = court || String(turf.courts[0]?.courtNumber ?? '')

  async function generate(e) {
    e.preventDefault()
    setGenBusy(true); setGenMsg(null)
    try {
      const r = await generateSlots(courtNo, turf.turfId, start)
      setGenMsg({ kind: 'success', text: `Created ${r.slotsCreated} slots for court ${r.courtNumber} (${formatDate(r.fromDate)} – ${formatDate(r.toDate)}).` })
      slotsQ.reload()
    } catch (err) { setGenMsg({ kind: 'error', text: err.message }) } finally { setGenBusy(false) }
  }

  async function toggle(slot) {
    setPending(slot.timeSlotId); setRowError(null)
    try { await setSlotStatus(turf.turfId, slot.timeSlotId, slot.status === 'FREE' ? 'BLOCKED' : 'FREE'); slotsQ.reload() }
    catch (err) { setRowError(err.message); slotsQ.reload() } finally { setPending(null) }
  }

  return (
    <>
      <p><Link to="/owner/turfs">← My turfs</Link></p>
      <h1>Time slots · {turf.name}</h1>
      <div className="panel">
        <h2>Generate slots</h2>
        <p className="muted small">Creates 30-minute slots for 7 days from the start date, for one court, using the turf's opening hours and the court's current price. Dates up to 60 days ahead.</p>
        {genMsg && <Notice kind={genMsg.kind}>{genMsg.text}</Notice>}
        <form onSubmit={generate} className="row" style={{ alignItems: 'end' }}>
          <Field label="Court">{(fid) => <select id={fid} value={courtNo} onChange={(e) => setCourt(e.target.value)}>{turf.courts.map((c) => <option key={c.courtNumber} value={c.courtNumber}>Court {c.courtNumber}</option>)}</select>}</Field>
          <Field label="Start date" type="date" min={todayISO()} required value={start} onChange={(e) => setStart(e.target.value)} />
          <div className="field"><Button type="submit" loading={genBusy} disabled={!start}>Generate</Button></div>
        </form>
      </div>

      {slotsQ.error?.status === 404 ? <EmptyState title="No slots yet">Generate slots above to open this turf for booking.</EmptyState>
        : <ErrorMessage error={slotsQ.error} onRetry={slotsQ.reload} />}
      {slotsQ.loading && !slotsQ.data && <Spinner />}
      {slots.length > 0 && (
        <>
          <div className="date-tabs" role="group" aria-label="Choose a date">
            {dates.map((d) => <button key={d} className="btn btn-secondary" aria-pressed={d === activeDate} onClick={() => setDate(d)}>{formatDate(d)}</button>)}
          </div>
          <p className="muted small">Click a free slot to block it (e.g. maintenance) or a blocked slot to free it. Reserved and booked slots belong to customers' bookings and cannot be changed here.</p>
          {rowError && <div className="alert alert-error" role="alert">{rowError}</div>}
          {byCourt.map(([c, list]) => (
            <section className="panel" key={c}>
              <h2>Court {c}</h2>
              <div className="slot-grid">
                {list.map((s) => {
                  const locked = s.status === 'HELD' || s.status === 'BOOKED' || hasStarted(s.slotDate, s.startTime)
                  return (
                    <button key={s.timeSlotId} className={`slot ${s.status.toLowerCase()}`} disabled={locked || pending === s.timeSlotId} onClick={() => toggle(s)}
                      title={s.status === 'FREE' ? 'Click to block' : s.status === 'BLOCKED' ? 'Click to free' : undefined}>
                      {formatTime(s.startTime)} – {formatTime(s.endTime)}
                      <small>{hasStarted(s.slotDate, s.startTime) ? `Past · ${s.status.toLowerCase()}` : s.status.toLowerCase()} · {formatMoney(s.price)}</small>
                    </button>
                  )
                })}
              </div>
            </section>
          ))}
        </>
      )}
    </>
  )
}
