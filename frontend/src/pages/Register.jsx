import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { Button, ErrorMessage, Field } from '../components/ui'
import * as authApi from '../services/auth'
import { EMAIL_RE, PHONE_RE } from '../utils/validation'

const ROLES = [
  { value: 'CUSTOMER', title: 'Customer', text: 'Book turf time slots' },
  { value: 'OWNER', title: 'Owner', text: 'List and manage turfs' },
]

export default function Register() {
  const navigate = useNavigate()
  const [f, setF] = useState({ role: '', name: '', email: '', phoneNumber: '', password: '', confirm: '', businessName: '' })
  const [touched, setTouched] = useState({})
  const [error, setError] = useState(null)
  const [serverFields, setServerFields] = useState({})
  const [busy, setBusy] = useState(false)
  const set = (k) => (e) => setF((s) => ({ ...s, [k]: e.target.value }))
  const blur = (k) => () => setTouched((t) => ({ ...t, [k]: true }))

  const errs = {
    role: f.role ? '' : 'Choose an account type',
    name: f.name.trim() ? '' : 'Name is required',
    email: !f.email.trim() ? 'Email is required' : !EMAIL_RE.test(f.email.trim()) ? 'Enter a valid email address' : '',
    phoneNumber: PHONE_RE.test(f.phoneNumber.trim()) ? '' : 'Use 7–15 digits, optionally starting with +',
    password: f.password ? '' : 'Password is required',
    confirm: f.confirm === f.password ? '' : 'Passwords do not match',
  }
  const valid = Object.values(errs).every((e) => !e)
  const show = (k) => (touched[k] && errs[k]) || serverFields[k] || ''

  async function submit(e) {
    e.preventDefault()
    if (!valid || busy) return
    setBusy(true)
    setError(null)
    setServerFields({})
    // Only fields the backend's SignupDTO has; confirm-password stays in the browser.
    const payload = { role: f.role, name: f.name.trim(), email: f.email.trim(), phoneNumber: f.phoneNumber.trim(), password: f.password }
    if (f.role === 'OWNER' && f.businessName.trim()) payload.businessName = f.businessName.trim()
    try {
      await authApi.signup(payload)
      navigate('/login', { replace: true, state: { registered: true, email: payload.email } })
    } catch (err) {
      setError(err.status === 409 ? 'An account with this email already exists. Try logging in instead.' : err.message)
      setServerFields(err.fieldErrors || {})
      setBusy(false)
    }
  }

  return (
    <div className="auth-wrap">
      <div className="panel">
        <h1>Create your account</h1>
        <ErrorMessage error={error} title="Could not create the account" />
        <form onSubmit={submit} noValidate>
          <fieldset className="role-pick">
            <legend>I am a…</legend>
            {ROLES.map((r) => (
              <label className="role-opt" key={r.value}>
                <input type="radio" name="role" value={r.value} checked={f.role === r.value} onChange={set('role')} />
                <strong>{r.title}</strong><span className="muted small">{r.text}</span>
              </label>
            ))}
          </fieldset>
          <Field label="Full name" required autoComplete="name" value={f.name} onChange={set('name')} onBlur={blur('name')} error={show('name')} />
          <Field label="Email" type="email" required autoComplete="email" value={f.email} onChange={set('email')} onBlur={blur('email')} error={show('email')} />
          <Field label="Phone number" type="tel" required autoComplete="tel" placeholder="+919876543210" value={f.phoneNumber} onChange={set('phoneNumber')} onBlur={blur('phoneNumber')} error={show('phoneNumber')} />
          {f.role === 'OWNER' && (
            <Field label="Business name" autoComplete="organization" value={f.businessName} onChange={set('businessName')} hint="Optional. Defaults to your name." />
          )}
          <Field label="Password" type="password" required autoComplete="new-password" value={f.password} onChange={set('password')} onBlur={blur('password')} error={show('password')} />
          <Field label="Confirm password" type="password" required autoComplete="new-password" value={f.confirm} onChange={set('confirm')} onBlur={blur('confirm')} error={show('confirm')} />
          <Button type="submit" disabled={!valid} loading={busy} className="btn-lg" style={{ width: '100%' }}>Sign up</Button>
        </form>
        <p style={{ marginTop: 16 }}>Already registered? <Link to="/login">Log in</Link></p>
      </div>
    </div>
  )
}
