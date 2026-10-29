import { useEffect, useId, useRef } from 'react'

export function Button({ variant = 'primary', loading = false, disabled, children, className = '', ...rest }) {
  return (
    <button className={`btn btn-${variant} ${className}`} disabled={disabled || loading} aria-busy={loading || undefined} {...rest}>
      {loading && <span className="spinner" aria-hidden="true" />}
      {children}
    </button>
  )
}

export function Field({ label, error, hint, children, ...input }) {
  const id = useId()
  const describedBy = [error && `${id}-err`, hint && `${id}-hint`].filter(Boolean).join(' ') || undefined
  return (
    <div className="field">
      <label htmlFor={id}>{label}{input.required && <span aria-hidden="true"> *</span>}</label>
      {children ? children(id, describedBy) : (
        <input id={id} aria-invalid={!!error} aria-describedby={describedBy} {...input} />
      )}
      {hint && !error && <p className="hint" id={`${id}-hint`}>{hint}</p>}
      {error && <p className="field-error" id={`${id}-err`}>{error}</p>}
    </div>
  )
}

export const Spinner = ({ label = 'Loading…' }) => (
  <div className="center-block" role="status"><span className="spinner spinner-lg" aria-hidden="true" /><span>{label}</span></div>
)

export const ErrorMessage = ({ error, onRetry, title = 'Something went wrong' }) => error ? (
  <div className="alert alert-error" role="alert">
    <strong>{title}</strong>
    <p>{typeof error === 'string' ? error : error.message}</p>
    {onRetry && <Button variant="secondary" onClick={onRetry}>Try again</Button>}
  </div>
) : null

export const Notice = ({ kind = 'info', children }) => (
  <div className={`alert alert-${kind}`} role={kind === 'error' ? 'alert' : 'status'}>{children}</div>
)

export const EmptyState = ({ title, children }) => (
  <div className="empty"><h3>{title}</h3>{children && <p>{children}</p>}</div>
)

const TONES = {
  CONFIRMED: 'good', ACTIVE: 'good', VERIFIED: 'good', FREE: 'good', ACTIVE_TURF: 'good',
  PENDING_RESERVATION: 'warn', PENDING: 'warn', RELEASE_PENDING: 'warn', HELD: 'warn', PARTIALLY_CANCELLED: 'warn',
  REJECTED: 'bad', CANCELLED: 'bad', BLOCKED: 'bad', HIDDEN: 'bad',
  BOOKED: 'neutral', OCCUPIED: 'neutral', PAST: 'neutral', NOT_COLLECTED: 'neutral',
}
const LABELS = { PENDING_RESERVATION: 'Awaiting confirmation', NOT_COLLECTED: 'Payment not collected', RELEASE_PENDING: 'Cancelling' }
export const StatusBadge = ({ status }) => (
  <span className={`badge badge-${TONES[status] || 'neutral'}`}>
    {LABELS[status] || status.replace(/_/g, ' ').toLowerCase().replace(/^\w/, (c) => c.toUpperCase())}
  </span>
)

/** Modal confirmation. Escape and the backdrop cancel; focus moves into the dialog while it is open. */
export function ConfirmDialog({ open, title, children, confirmLabel = 'Confirm', danger, busy, onConfirm, onCancel }) {
  const ref = useRef(null)
  useEffect(() => {
    if (!open) return undefined
    const prev = document.activeElement
    ref.current?.querySelector('button')?.focus()
    const onKey = (e) => { if (e.key === 'Escape' && !busy) onCancel() }
    document.addEventListener('keydown', onKey)
    return () => { document.removeEventListener('keydown', onKey); prev?.focus?.() }
  }, [open, busy, onCancel])
  if (!open) return null
  return (
    <div className="backdrop" onMouseDown={(e) => e.target === e.currentTarget && !busy && onCancel()}>
      <div className="dialog" role="alertdialog" aria-modal="true" aria-labelledby="dlg-title" ref={ref}>
        <h2 id="dlg-title">{title}</h2>
        <div className="dialog-body">{children}</div>
        <div className="dialog-actions">
          <Button variant="secondary" onClick={onCancel} disabled={busy}>Keep as is</Button>
          <Button variant={danger ? 'danger' : 'primary'} onClick={onConfirm} loading={busy}>{confirmLabel}</Button>
        </div>
      </div>
    </div>
  )
}

export function Pagination({ page, totalPages, onChange }) {
  if (!totalPages || totalPages <= 1) return null
  return (
    <nav className="pagination" aria-label="Pagination">
      <Button variant="secondary" disabled={page <= 0} onClick={() => onChange(page - 1)}>Previous</Button>
      <span>Page {page + 1} of {totalPages}</span>
      <Button variant="secondary" disabled={page + 1 >= totalPages} onClick={() => onChange(page + 1)}>Next</Button>
    </nav>
  )
}
