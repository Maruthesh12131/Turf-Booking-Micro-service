import { useState } from 'react'
import { Link } from 'react-router-dom'
import { ConfirmDialog, EmptyState, ErrorMessage, Notice, Spinner, StatusBadge } from '../../components/ui'
import { useAsync } from '../../hooks/useAsync'
import { deleteTurf, myTurfs } from '../../services/turfs'
import { formatMoney, formatTime } from '../../utils/format'

export default function OwnerTurfs() {
  const { data, error, loading, reload } = useAsync((s) => myTurfs(s), [])
  const [target, setTarget] = useState(null)
  const [busy, setBusy] = useState(false)
  const [delError, setDelError] = useState(null)

  async function remove() {
    setBusy(true); setDelError(null)
    try { await deleteTurf(target.turfId); setTarget(null); reload() } catch (e) { setDelError(e.message) } finally { setBusy(false) }
  }

  return (
    <>
      <div className="page-head"><h1>My turfs</h1><Link className="btn btn-primary" to="/owner/turfs/new">+ Add a turf</Link></div>
      <ErrorMessage error={error} onRetry={reload} />
      {loading && <Spinner />}
      {data?.length === 0 && <EmptyState title="You have no turfs yet">Add your first turf (your account must be verified).</EmptyState>}
      <div className="stack">
        {data?.map((t) => (
          <div className="panel" key={t.turfId}>
            <div className="page-head" style={{ marginBottom: 8 }}>
              <div><h2>{t.name}</h2><p className="muted">📍 {t.location} · {formatTime(t.openingTime)} – {formatTime(t.closingTime)}</p></div>
              <StatusBadge status={t.status} />
            </div>
            {t.status === 'HIDDEN' && <Notice kind="warn">Hidden from customers (your verification is not approved).</Notice>}
            <p className="muted">{t.courts.map((c) => `Court ${c.courtNumber}: ${formatMoney(c.amountPerHour)}/hr`).join(' · ')}</p>
            <div className="inline">
              <Link className="btn btn-secondary" to={`/owner/turfs/${t.turfId}/slots`}>Time slots</Link>
              <Link className="btn btn-secondary" to={`/owner/turfs/${t.turfId}/edit`}>Edit</Link>
              <button className="btn btn-secondary" onClick={() => { setDelError(null); setTarget(t) }}>Delete…</button>
            </div>
          </div>
        ))}
      </div>
      <ConfirmDialog open={!!target} danger busy={busy} title={`Delete “${target?.name}”?`} confirmLabel="Delete turf" onConfirm={remove} onCancel={() => setTarget(null)}>
        <p>This permanently removes the turf, its courts and all of its time slots. It is refused while any slot is reserved or booked.</p>
        {delError && <div className="alert alert-error" role="alert">{delError}</div>}
      </ConfirmDialog>
    </>
  )
}
