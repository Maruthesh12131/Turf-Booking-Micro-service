import { useEffect, useRef, useState } from 'react'
import { Link, NavLink, useLocation, useNavigate, useSearchParams } from 'react-router-dom'
import { APP_NAME } from '../config'
import { homePathFor, useAuth } from '../context/AuthContext'
import { useDebounce } from '../hooks/useDebounce'

function ProfileMenu() {
  const { isAuthenticated, profile, email, role, logout } = useAuth()
  const [open, setOpen] = useState(false)
  const ref = useRef(null)
  const btn = useRef(null)
  const navigate = useNavigate()
  const { pathname } = useLocation()
  useEffect(() => setOpen(false), [pathname])
  useEffect(() => {
    if (!open) return undefined
    const onDoc = (e) => { if (!ref.current?.contains(e.target)) setOpen(false) }
    const onKey = (e) => { if (e.key === 'Escape') { setOpen(false); btn.current?.focus() } }
    document.addEventListener('mousedown', onDoc)
    document.addEventListener('touchstart', onDoc)
    document.addEventListener('keydown', onKey)
    return () => { document.removeEventListener('mousedown', onDoc); document.removeEventListener('touchstart', onDoc); document.removeEventListener('keydown', onKey) }
  }, [open])

  const label = isAuthenticated ? (profile?.name || email) : 'Account'
  return (
    <div className="menu" ref={ref} onMouseEnter={(e) => e.pointerType !== 'touch' && matchMedia('(hover:hover)').matches && setOpen(true)} onMouseLeave={() => matchMedia('(hover:hover)').matches && setOpen(false)}>
      <button ref={btn} className="btn btn-secondary profile-btn" aria-haspopup="menu" aria-expanded={open} onClick={() => setOpen((o) => !o)}>
        <span className="avatar" aria-hidden="true">{isAuthenticated ? (label?.[0] || '?').toUpperCase() : '👤'}</span>
        <span className="profile-label">{label}</span>
      </button>
      {open && (
        <ul className="dropdown" role="menu">
          {isAuthenticated ? (
            <>
              <li className="dropdown-head" role="presentation">{email}<br /><small>{role}</small></li>
              <li role="none"><Link role="menuitem" to={homePathFor(role) === '/' ? '/account' : homePathFor(role)}>{role === 'CUSTOMER' ? 'My account' : 'Dashboard'}</Link></li>
              <li role="none"><button role="menuitem" onClick={() => { logout(); navigate('/') }}>Log out</button></li>
            </>
          ) : (
            <>
              <li role="none"><Link role="menuitem" to="/register">Sign Up</Link></li>
              <li role="none"><Link role="menuitem" to="/login">Log In</Link></li>
            </>
          )}
        </ul>
      )}
    </div>
  )
}

/** Location search: debounced, writes ?location= on the home page, which queries GET /turfs?location=. */
function LocationSearch() {
  const navigate = useNavigate()
  const { pathname } = useLocation()
  const [params] = useSearchParams()
  const urlValue = pathname === '/' ? params.get('location') || '' : ''
  const [text, setText] = useState(urlValue)
  const debounced = useDebounce(text, 400)
  useEffect(() => setText(urlValue), [urlValue])
  useEffect(() => {
    if (debounced.trim() === urlValue.trim()) return
    if (pathname !== '/' && !debounced.trim()) return
    navigate(debounced.trim() ? `/?location=${encodeURIComponent(debounced.trim())}` : '/', { replace: pathname === '/' })
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [debounced])
  return (
    <form className="search" role="search" onSubmit={(e) => { e.preventDefault(); navigate(text.trim() ? `/?location=${encodeURIComponent(text.trim())}` : '/') }}>
      <label htmlFor="loc-search" className="sr-only">Search turfs by location</label>
      <input id="loc-search" type="search" placeholder="Search by location…" value={text} onChange={(e) => setText(e.target.value)} />
      {text && <button type="button" className="clear" aria-label="Clear search" onClick={() => { setText(''); navigate('/') }}>×</button>}
    </form>
  )
}

export default function Header() {
  const { role, isAuthenticated } = useAuth()
  const [navOpen, setNavOpen] = useState(false)
  const { pathname } = useLocation()
  useEffect(() => setNavOpen(false), [pathname])
  const customerFacing = !isAuthenticated || role === 'CUSTOMER'
  return (
    <header className="header">
      <div className="container header-inner">
        <Link to={homePathFor(role)} className="logo">⚽ {APP_NAME}</Link>
        <button className="btn btn-secondary nav-toggle" aria-expanded={navOpen} aria-controls="main-nav" onClick={() => setNavOpen((o) => !o)}>Menu</button>
        <nav id="main-nav" className={`nav ${navOpen ? 'open' : ''}`} aria-label="Main">
          {customerFacing && <NavLink to="/" end>Turfs</NavLink>}
          {role === 'CUSTOMER' && <NavLink to="/bookings">My bookings</NavLink>}
          {role === 'OWNER' && <><NavLink to="/owner" end>Dashboard</NavLink><NavLink to="/owner/turfs">My turfs</NavLink><NavLink to="/owner/bookings">Bookings</NavLink><NavLink to="/owner/profile">Business</NavLink></>}
          {role === 'ADMIN' && <NavLink to="/admin">Admin</NavLink>}
        </nav>
        {customerFacing && <LocationSearch />}
        <ProfileMenu />
      </div>
    </header>
  )
}
