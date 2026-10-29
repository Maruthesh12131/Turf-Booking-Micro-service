import { useEffect, useState } from 'react'
import { Button, ErrorMessage, Field, Notice, Spinner, StatusBadge } from '../../components/ui'
import { useAsync } from '../../hooks/useAsync'
import { createOwnerProfile, getOwnerProfile, updateOwnerProfile } from '../../services/owners'
import { formatDateTime } from '../../utils/format'
import { PHONE_RE } from '../../utils/validation'

export default function OwnerProfile() {
  const { data, error, loading, reload } = useAsync((s) => getOwnerProfile(s), [])
  const missing = error?.status === 404
  const [f, setF] = useState({ businessName: '', phoneNumber: '' })
  const [busy, setBusy] = useState(false)
  const [saveError, setSaveError] = useState(null)
  const [saved, setSaved] = useState(false)
  useEffect(() => { if (data) setF({ businessName: data.businessName, phoneNumber: data.phoneNumber }) }, [data])

  if (loading) return <Spinner />
  if (error && !missing) return <ErrorMessage error={error} onRetry={reload} />

  const errs = {
    businessName: !f.businessName.trim() ? 'Business name is required' : f.businessName.length > 150 ? 'At most 150 characters' : '',
    phoneNumber: PHONE_RE.test(f.phoneNumber) ? '' : 'Use 7–15 digits, optionally starting with +',
  }
  const valid = !errs.businessName && !errs.phoneNumber

  async function submit(e) {
    e.preventDefault()
    if (!valid || busy) return
    setBusy(true); setSaveError(null); setSaved(false)
    try {
      await (missing ? createOwnerProfile : updateOwnerProfile)({ businessName: f.businessName.trim(), phoneNumber: f.phoneNumber.trim() })
      setSaved(true)
      reload()
    } catch (err) { setSaveError(err) } finally { setBusy(false) }
  }

  return (
    <div style={{ maxWidth: 560 }}>
      <h1>Business details</h1>
      {data && <div className="panel"><p className="inline"><strong>Verification:</strong> <StatusBadge status={data.verificationStatus} /></p><p className="muted small">Owner ID {data.ownerId} · created {formatDateTime(data.createdAt)}. Verification is decided by an administrator.</p></div>}
      {missing && <Notice kind="warn">No owner profile exists for your account yet. Fill this in to create it.</Notice>}
      <div className="panel">
        {saved && <Notice kind="success">Saved.</Notice>}
        <ErrorMessage error={saveError} title="Could not save" />
        <form onSubmit={submit} noValidate>
          <Field label="Business name" required value={f.businessName} error={f.businessName !== '' || saveError ? errs.businessName : ''} onChange={(e) => setF({ ...f, businessName: e.target.value })} />
          <Field label="Phone number" type="tel" required value={f.phoneNumber} error={f.phoneNumber !== '' ? errs.phoneNumber : ''} onChange={(e) => setF({ ...f, phoneNumber: e.target.value })} />
          <Button type="submit" disabled={!valid} loading={busy}>{missing ? 'Create profile' : 'Save changes'}</Button>
        </form>
      </div>
    </div>
  )
}
