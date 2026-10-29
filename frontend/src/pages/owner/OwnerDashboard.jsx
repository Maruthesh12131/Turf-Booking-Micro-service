import { Link } from 'react-router-dom'
import { EmptyState, ErrorMessage, Notice, Spinner, StatusBadge } from '../../components/ui'
import { useAsync } from '../../hooks/useAsync'
import { ownerBookings } from '../../services/bookings'
import { getOwnerProfile } from '../../services/owners'
import { myTurfs } from '../../services/turfs'

// Every number here is a count of rows the API actually returned; nothing is estimated.
export default function OwnerDashboard() {
  const profile = useAsync((s) => getOwnerProfile(s), [])
  const turfs = useAsync((s) => myTurfs(s), [])
  const bookings = useAsync((s) => ownerBookings({ size: 1 }, s), [])

  if (profile.loading || turfs.loading) return <Spinner />
  if (profile.error?.status === 404) {
    return <EmptyState title="No owner profile yet">Your owner profile is created right after sign-up. If it is missing, <Link to="/owner/profile">create it here</Link>.</EmptyState>
  }
  if (profile.error) return <ErrorMessage error={profile.error} onRetry={profile.reload} />
  const p = profile.data
  const verified = p.verificationStatus === 'VERIFIED'

  return (
    <>
      <div className="page-head"><div><h1>Owner dashboard</h1><p className="muted">{p.businessName}</p></div><StatusBadge status={p.verificationStatus} /></div>
      {p.verificationStatus === 'PENDING' && <Notice kind="warn">Your account is waiting for admin verification. You can prepare your business details, but new turfs can only be created once you are verified.</Notice>}
      {p.verificationStatus === 'REJECTED' && <Notice kind="error">Your verification was rejected. Turfs cannot be created or shown to customers.</Notice>}
      <div className="grid" style={{ marginBottom: 20 }}>
        <div className="panel"><h2>{turfs.data?.length ?? '–'}</h2><p className="muted">Turfs</p><Link to="/owner/turfs">Manage turfs</Link></div>
        <div className="panel"><h2>{bookings.data ? bookings.data.totalElements : bookings.error ? '–' : '…'}</h2><p className="muted">Bookings received</p><Link to="/owner/bookings">View bookings</Link></div>
        <div className="panel"><h2>{p.verificationStatus.charAt(0) + p.verificationStatus.slice(1).toLowerCase()}</h2><p className="muted">Verification</p><Link to="/owner/profile">Business details</Link></div>
      </div>
      {verified && <Link className="btn btn-primary" to="/owner/turfs/new">+ Add a turf</Link>}
      <ErrorMessage error={turfs.error} onRetry={turfs.reload} title="Could not load your turfs" />
    </>
  )
}
