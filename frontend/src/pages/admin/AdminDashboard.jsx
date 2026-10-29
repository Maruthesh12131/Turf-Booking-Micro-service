import { useState } from 'react'
import { Button, ConfirmDialog, EmptyState, ErrorMessage, Field, Notice, Pagination, Spinner } from '../../components/ui'
import { useAsync } from '../../hooks/useAsync'
import { setOwnerVerification } from '../../services/owners'
import { deleteTurf, searchTurfs } from '../../services/turfs'

// The backend gives admins exactly two powers: PUT /owners/{id}/verification and DELETE /turfs/{id}.
// There is no endpoint to list owners, so verification takes an owner ID.
export default function AdminDashboard() {
  const [ownerId, setOwnerId] = useState('')
  const [status, setStatus] = useState('VERIFIED')
  const [reason, setReason] = useState('')
  const [busy, setBusy] = useState(false)
  const [result, setResult] = useState(null)
  const [page, setPage] = useState(0)
  const turfs = useAsync((s) => searchTurfs({ page, size: 10 }, s), [page])
  const [target, setTarget] = useState(null)
  const [delBusy, setDelBusy] = useState(false)
  const [delError, setDelError] = useState(null)

  async function verify(e) {
    e.preventDefault()
    setBusy(true); setResult(null)
    try {
      const o = await setOwnerVerification(ownerId, status, reason.trim())
      setResult({ kind: 'success', text: `Owner ${o.ownerId} (${o.businessName}) is now ${o.verificationStatus}.` })
    } catch (err) { setResult({ kind: 'error', text: err.message }) } finally { setBusy(false) }
  }

  async function remove() {
    setDelBusy(true); setDelError(null)
    try { await deleteTurf(target.turfId); setTarget(null); turfs.reload() } catch (err) { setDelError(err.message) } finally { setDelBusy(false) }
  }

  return (
    <>
      <h1>Admin</h1>
      <div className="panel" style={{ maxWidth: 620 }}>
        <h2>Owner verification</h2>
        <Notice>The backend has no owner listing yet, so enter the owner ID you want to review.</Notice>
        {result && <Notice kind={result.kind}>{result.text}</Notice>}
        <form onSubmit={verify}>
          <div className="row">
            <Field label="Owner ID" type="number" min="1" required value={ownerId} onChange={(e) => setOwnerId(e.target.value)} />
            <Field label="Decision">{(id) => <select id={id} value={status} onChange={(e) => setStatus(e.target.value)}><option value="VERIFIED">Verify</option><option value="REJECTED">Reject</option></select>}</Field>
          </div>
          <Field label="Reason (optional)" maxLength={500} value={reason} onChange={(e) => setReason(e.target.value)} />
          <Button type="submit" loading={busy} disabled={!ownerId}>Submit decision</Button>
        </form>
      </div>

      <h2>Listed turfs</h2>
      <p className="muted small">Active turfs visible to customers. Admins may delete a turf that has no reserved or booked slots.</p>
      <ErrorMessage error={turfs.error} onRetry={turfs.reload} />
      {turfs.loading && <Spinner />}
      {turfs.data?.content.length === 0 && <EmptyState title="No turfs listed" />}
      {turfs.data && !turfs.loading && turfs.data.content.length > 0 && (
        <div className="panel table-wrap">
          <table>
            <thead><tr><th>ID</th><th>Name</th><th>Location</th><th /></tr></thead>
            <tbody>{turfs.data.content.map((t) => (
              <tr key={t.turfId}><td>{t.turfId}</td><td>{t.name}</td><td>{t.location}</td><td><button className="btn btn-secondary" onClick={() => { setDelError(null); setTarget(t) }}>Delete…</button></td></tr>
            ))}</tbody>
          </table>
        </div>
      )}
      {turfs.data && <Pagination page={turfs.data.page} totalPages={turfs.data.totalPages} onChange={setPage} />}
      <ConfirmDialog open={!!target} danger busy={delBusy} title={`Delete “${target?.name}”?`} confirmLabel="Delete turf" onConfirm={remove} onCancel={() => setTarget(null)}>
        <p>This permanently removes the turf and its time slots.</p>
        {delError && <div className="alert alert-error" role="alert">{delError}</div>}
      </ConfirmDialog>
    </>
  )
}
