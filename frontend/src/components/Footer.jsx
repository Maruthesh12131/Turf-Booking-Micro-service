import { Link } from 'react-router-dom'
import { APP_NAME } from '../config'
import { useAuth } from '../context/AuthContext'

export default function Footer() {
  const { role, isAuthenticated } = useAuth()
  return (
    <footer className="footer">
      <div className="container footer-inner">
        <div><strong>⚽ {APP_NAME}</strong><p className="muted small">Find a turf, pick your time slots and book.</p></div>
        <nav aria-label="Footer">
          <Link to="/">Browse turfs</Link>
          {role === 'CUSTOMER' && <Link to="/bookings">My bookings</Link>}
          {role === 'OWNER' && <Link to="/owner">Owner dashboard</Link>}
          {!isAuthenticated && <><Link to="/login">Log in</Link><Link to="/register">Sign up</Link></>}
        </nav>
        <p className="muted small">© {new Date().getFullYear()} {APP_NAME}. Payments are not processed in this app.</p>
      </div>
    </footer>
  )
}
