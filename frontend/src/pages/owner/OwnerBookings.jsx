import { useState } from 'react'
import { EmptyState, ErrorMessage, Field, Pagination, Spinner, StatusBadge } from '../../components/ui'
import { useAsync } from '../../hooks/useAsync'
import { ownerBookings } from '../../services/bookings'
import { myTurfs } from '../../services/turfs'
import { formatDate, formatDateTime, formatMoney, formatTime } from '../../utils/format'

export default function OwnerBookings() {
  const [turfId, setTurfId] = useState('')
  const [date, setDate] = useState('')
  const [page, setPage] = useState(0)
  const turfs = useAsync((s) => myTurfs(s), [])
  const { data, error, loading, reload } = useAsync((s) => ownerBookings({ turfId, date, page, size: 10 }, s), [turfId, date, page])
  const nameOf = (id) => turfs.data?.find((t) => t.turfId === id)?.name || `Turf #${id}`

  return (
    <>
      <h1>Bookings on my turfs</h1>
      <div className="row" style={{ maxWidth: 560 }}>
        <Field label="Turf">{(id) => <select id={id} value={turfId} onChange={(e) => { setTurfId(e.target.value); setPage(0) }}><option value="">All turfs</option>{turfs.data?.map((t) => <option key={t.turfId} value={t.turfId}>{t.name}</option>)}</select>}</Field>
        <Field label="Date" type="date" value={date} onChange={(e) => { setDate(e.target.value); setPage(0) }} />
      </div>
      <ErrorMessage error={error} onRetry={reload} />
      {loading && <Spinner />}
      {data?.content.length === 0 && <EmptyState title="No bookings found" />}
      {data && !loading && data.content.length > 0 && (
        <div className="panel table-wrap">
          <table>
            <thead><tr><th>Booking</th><th>Turf / court</th><th>When</th><th>Customer</th><th>Amount</th><th>Status</th></tr></thead>
            <tbody>
              {data.content.map((b) => (
                <tr key={b.bookingId}>
                  <td>#{b.bookingId}<br /><span className="muted small">{formatDateTime(b.createdAt)}</span></td>
                  <td>{nameOf(b.turfId)}<br />Court {b.courtNumber}</td>
                  <td>{formatDate(b.bookingDate)}<br />{b.items.map((i) => `${formatTime(i.startTime)}`).join(', ')}</td>
                  <td>{b.customerName}<br /><a href={`tel:${b.customerPhone}`}>{b.customerPhone}</a></td>
                  <td>{formatMoney(b.currentAmount)}</td>
                  <td><StatusBadge status={b.status} /></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {data && <Pagination page={data.page} totalPages={data.totalPages} onChange={setPage} />}
    </>
  )
}
