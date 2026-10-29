import { useState } from 'react'
import { Link, useLocation, useNavigate } from 'react-router-dom'
import { Button, ErrorMessage, Field, Notice } from '../components/ui'
import { homePathFor, useAuth } from '../context/AuthContext'
import { EMAIL_RE } from '../utils/validation'

export default function Login() {
  const { login, notice } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [email, setEmail] = useState(location.state?.email || '')
  const [password, setPassword] = useState('')
  const [show, setShow] = useState(false)
  const [touched, setTouched] = useState({})
  const [error, setError] = useState(null)
  const [busy, setBusy] = useState(false)

  const errs = {
    email: !email.trim() ? 'Email is required' : !EMAIL_RE.test(email.trim()) ? 'Enter a valid email address' : '',
    password: password ? '' : 'Password is required',
  }

  async function submit(e) {
    e.preventDefault()
    setTouched({ email: true, password: true })
    if (busy || errs.email || errs.password) return
    setBusy(true)
    setError(null)
    try {
      const user = await login(email.trim(), password)
      // Go back to where the user was heading only if their role may use it; otherwise to their own home.
      const from = location.state?.from
      navigate(user.role === 'CUSTOMER' && from ? from : homePathFor(user.role), { replace: true })
    } catch (err) {
      setError(err.status === 401 || err.status === 403 ? 'Incorrect email or password.' : err.message)
      setBusy(false)
    }
  }

  return (
    <div className="auth-wrap">
      <div className="panel">
        <h1>Log in</h1>
        {location.state?.registered && <Notice kind="success">Account created. Log in to continue.</Notice>}
        {notice && <Notice kind="warn">{notice}</Notice>}
        {location.state?.from && !notice && <Notice>Please log in to continue.</Notice>}
        <ErrorMessage error={error} title="Could not log in" />
        <form onSubmit={submit} noValidate>
          <Field label="Email" type="email" autoComplete="email" required value={email} onChange={(e) => setEmail(e.target.value)} onBlur={() => setTouched((t) => ({ ...t, email: true }))} error={touched.email && errs.email} />
          <Field label="Password" required error={touched.password && errs.password}>
            {(id, describedBy) => (
              <div className="input-row">
                <input id={id} type={show ? 'text' : 'password'} autoComplete="current-password" value={password} aria-describedby={describedBy} aria-invalid={!!(touched.password && errs.password)} onChange={(e) => setPassword(e.target.value)} onBlur={() => setTouched((t) => ({ ...t, password: true }))} />
                <button type="button" onClick={() => setShow((s) => !s)} aria-pressed={show}>{show ? 'Hide' : 'Show'}</button>
              </div>
            )}
          </Field>
          <Button type="submit" loading={busy} className="btn-lg" style={{ width: '100%' }}>Log in</Button>
        </form>
        <p style={{ marginTop: 16 }}>New here? <Link to="/register">Create an account</Link></p>
      </div>
    </div>
  )
}
