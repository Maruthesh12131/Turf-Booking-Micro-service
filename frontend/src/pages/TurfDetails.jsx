import { Link, useNavigate, useParams } from 'react-router-dom'
import { Button, EmptyState, ErrorMessage, Spinner } from '../components/ui'
import { useAuth } from '../context/AuthContext'
import { useAsync } from '../hooks/useAsync'
import { getTurf } from '../services/turfs'
import { formatMoney, formatTime } from '../utils/format'

export default function TurfDetails() {
  const { id } = useParams()
  const { role } = useAuth()
  const navigate = useNavigate()
  const { data: turf, error, loading, reload } = useAsync((s) => getTurf(id, s), [id])

  if (loading) return <Spinner />
  if (error?.status === 404) return <EmptyState title="Turf not found">It may have been removed or is not available. <Link to="/">Back to all turfs</Link></EmptyState>
  if (error) return <ErrorMessage error={error} onRetry={reload} />

  const slotsPath = `/turfs/${turf.turfId}/slots`
  const canBook = !role || role === 'CUSTOMER'
  return (
    <>
      <p><Link to="/">← All turfs</Link></p>
      <div className="panel">
        <div className="page-head">
          <div>
            <h1>{turf.name}</h1>
            <p className="muted">📍 {turf.location}</p>
          </div>
          {canBook && (
            <Button className="btn-lg" onClick={() => navigate(slotsPath)}>Show Time Slots</Button>
          )}
        </div>
        <div className="facts">
          <span><strong>Open:</strong> {formatTime(turf.openingTime)} – {formatTime(turf.closingTime)}{turf.openingTime === turf.closingTime && ' (24 hours)'}</span>
          <span><strong>Courts:</strong> {turf.courts?.length || 0}</span>
        </div>
        {turf.description ? <p>{turf.description}</p> : <p className="muted">No description provided.</p>}
        {!role && <p className="muted small">You will be asked to log in to see time slots.</p>}
      </div>
      <h2>Courts</h2>
      {turf.courts?.length ? (
        <div className="court-list">
          {turf.courts.map((c) => (
            <div className="card" key={c.courtNumber}>
              <h3>Court {c.courtNumber}</h3>
              <p><strong>{formatMoney(c.amountPerHour)}</strong> / hour</p>
              {(c.length || c.width) && <p className="muted small">Size: {c.length ?? '?'} × {c.width ?? '?'}</p>}
              {c.sports?.length > 0 && <p className="chips">{c.sports.map((s) => <span className="chip" key={s.sportId}>{s.sportName}</span>)}</p>}
            </div>
          ))}
        </div>
      ) : <p className="muted">No courts listed.</p>}
    </>
  )
}
