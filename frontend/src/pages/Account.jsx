import { useNavigate } from 'react-router-dom'
import { Link } from 'react-router-dom'
import { Button, Spinner } from '../components/ui'
import { useAuth } from '../context/AuthContext'

export default function Account() {
  const { profile, email, role, logout } = useAuth()
  const navigate = useNavigate()
  if (!profile) return <Spinner />
  return (
    <>
      <h1>My account</h1>
      <div className="panel stack">
        <p><strong>Name:</strong> {profile.name}</p>
        <p><strong>Email:</strong> {email}</p>
        <p><strong>Phone:</strong> {profile.phoneNumber}</p>
        <p><strong>Account type:</strong> {role}</p>
        <p className="muted small">The backend offers no endpoint to edit these details, so they are read-only here.</p>
        <div className="inline">
          {role === 'CUSTOMER' && <Link className="btn btn-secondary" to="/bookings">My bookings</Link>}
          <Button variant="danger" onClick={() => { logout(); navigate('/') }}>Log out</Button>
        </div>
      </div>
    </>
  )
}
