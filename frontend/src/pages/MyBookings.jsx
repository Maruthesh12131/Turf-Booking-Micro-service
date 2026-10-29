import { useState } from 'react'
import { Link } from 'react-router-dom'
import { EmptyState, ErrorMessage, Pagination, Spinner, StatusBadge } from '../components/ui'
import { useAsync } from '../hooks/useAsync'
import { myBookings } from '../services/bookings'
import { formatDate, formatMoney, formatTime } from '../utils/format'

export default function MyBookings() {
  const [page, setPage] = useState(0)
  const { data, error, loading, reload } = useAsync((s) => myBookings({ page, size: 10 }, s), [page])
  return (
    <>
      <h1>My bookings</h1>
      <ErrorMessage error={error} onRetry={reload} title="Could not load bookings" />
      {loading && <Spinner />}
      {data && data.content.length === 0 && <EmptyState title="No bookings yet"><Link to="/">Browse turfs</Link> to make your first booking.</EmptyState>}
      {data && !loading && data.content.length > 0 && (
        <div className="stack">
          {data.content.map((b) => {
            const first = b.items[0]
            return (
              <Link className="card booking-link" key={b.bookingId} to={`/bookings/${b.bookingId}`}>
                <div className="inline" style={{ justifyContent: 'space-between' }}>
                  <strong>Booking #{b.bookingId} · Turf #{b.turfId} · Court {b.courtNumber}</strong>
                  <StatusBadge status={b.status} />
                </div>
                <p className="muted">{formatDate(b.bookingDate)}{first && ` · ${formatTime(first.startTime)} – ${formatTime(b.items.at(-1).endTime)}`} · {b.items.length} slot{b.items.length === 1 ? '' : 's'} · {formatMoney(b.currentAmount)}</p>
              </Link>
            )
          })}
          <Pagination page={data.page} totalPages={data.totalPages} onChange={setPage} />
        </div>
      )}
    </>
  )
}
