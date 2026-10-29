import { Link } from 'react-router-dom'
import { formatMoney, formatTime } from '../utils/format'

// The API returns no images, ratings or turf-level price, so the card shows a neutral placeholder plus real fields only.
export default function TurfCard({ turf }) {
  const prices = (turf.courts || []).map((c) => c.amountPerHour).filter((p) => p != null)
  const sports = [...new Set((turf.courts || []).flatMap((c) => (c.sports || []).map((s) => s.sportName)))]
  const from = prices.length ? Math.min(...prices) : null
  return (
    <Link to={`/turfs/${turf.turfId}`} className="card turf-card">
      <div className="turf-thumb" aria-hidden="true">{turf.name?.[0]?.toUpperCase()}</div>
      <div className="card-body">
        <h3>{turf.name}</h3>
        <p className="muted">📍 {turf.location}</p>
        {turf.openingTime && <p className="muted small">Open {formatTime(turf.openingTime)} – {formatTime(turf.closingTime)}</p>}
        {sports.length > 0 && <p className="chips">{sports.slice(0, 3).map((s) => <span className="chip" key={s}>{s}</span>)}{sports.length > 3 && <span className="chip">+{sports.length - 3}</span>}</p>}
        <p className="card-foot">
          <span>{turf.courts?.length || 0} court{turf.courts?.length === 1 ? '' : 's'}</span>
          {from != null && <strong>from {formatMoney(from)}/hr</strong>}
        </p>
      </div>
    </Link>
  )
}

export const TurfCardSkeleton = () => (
  <div className="card turf-card skeleton" aria-hidden="true"><div className="turf-thumb" /><div className="card-body"><div className="sk-line" /><div className="sk-line short" /><div className="sk-line" /></div></div>
)
