import { Navigate, useLocation } from 'react-router-dom'
import { homePathFor, useAuth } from '../context/AuthContext'
import { EmptyState } from './ui'

/** UI convenience only: the services enforce roles. Guests go to login (and come back); wrong roles see a notice. */
export default function RequireRole({ roles, children }) {
  const { isAuthenticated, role } = useAuth()
  const location = useLocation()
  if (!isAuthenticated) return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />
  if (!roles.includes(role)) {
    return <EmptyState title="Not available for your account">This page is for {roles.join(' / ').toLowerCase()} accounts. <a href={homePathFor(role)}>Go to your home page</a>.</EmptyState>
  }
  return children
}
