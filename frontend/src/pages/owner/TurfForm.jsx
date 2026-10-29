import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { Button, ErrorMessage, Field, Spinner } from '../../components/ui'
import { useAsync } from '../../hooks/useAsync'
import { createTurf, listSports, myTurfs, updateTurf } from '../../services/turfs'

const blankCourt = () => ({ amountPerHour: '', length: '', width: '', sports: '' })
const hhmm = (t) => (t ? t.slice(0, 5) : '')

// Create: POST /turfs (TurfDTO). Edit: PUT /turfs/{id}; the API only updates existing courts, it cannot add or remove them.
export default function TurfForm() {
  const { id } = useParams()
  const editing = !!id
  const navigate = useNavigate()
  const existing = useAsync(async (s) => (editing ? (await myTurfs(s)).find((t) => String(t.turfId) === id) || null : null), [id])
  const sportsQ = useAsync((s) => listSports(s), [])
  const [f, setF] = useState({ name: '', location: '', description: '', openingTime: '06:00', closingTime: '22:00' })
  const [courts, setCourts] = useState([blankCourt()])
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState(null)
  const [tried, setTried] = useState(false)

  useEffect(() => {
    const t = existing.data
    if (!t) return
    setF({ name: t.name, location: t.location, description: t.description || '', openingTime: hhmm(t.openingTime), closingTime: hhmm(t.closingTime) })
    setCourts(t.courts.map((c) => ({ courtNumber: c.courtNumber, amountPerHour: String(c.amountPerHour), length: c.length ?? '', width: c.width ?? '', sports: c.sports.map((s) => s.sportName).join(', ') })))
  }, [existing.data])

  if (existing.loading) return <Spinner />
  if (editing && !existing.data) return <ErrorMessage error={existing.error || 'Turf not found among your turfs.'} onRetry={existing.reload} />

  const setCourt = (i, k) => (e) => setCourts((cs) => cs.map((c, j) => (j === i ? { ...c, [k]: e.target.value } : c)))
  const errs = {
    name: f.name.trim() ? '' : 'Name is required',
    location: f.location.trim() ? '' : 'Location is required',
    openingTime: f.openingTime ? '' : 'Opening time is required',
    closingTime: f.closingTime ? '' : 'Closing time is required',
  }
  const courtErr = courts.map((c) => ({
    amountPerHour: Number(c.amountPerHour) > 0 && Number.isInteger(Number(c.amountPerHour)) ? '' : 'Enter a whole price above 0',
    length: c.length === '' || (Number(c.length) > 0 && Number.isInteger(Number(c.length))) ? '' : 'Whole number',
    width: c.width === '' || (Number(c.width) > 0 && Number.isInteger(Number(c.width))) ? '' : 'Whole number',
  }))
  const valid = Object.values(errs).every((e) => !e) && courtErr.every((c) => Object.values(c).every((e) => !e))

  async function submit(e) {
    e.preventDefault()
    setTried(true)
    if (!valid || busy) return
    setBusy(true); setError(null)
    const body = {
      name: f.name.trim(), location: f.location.trim(), description: f.description.trim() || null,
      openingTime: f.openingTime, closingTime: f.closingTime,
      courts: courts.map((c) => ({
        ...(editing ? { courtNumber: c.courtNumber } : {}),
        amountPerHour: Number(c.amountPerHour),
        length: c.length === '' ? null : Number(c.length),
        width: c.width === '' ? null : Number(c.width),
        sports: c.sports.split(',').map((s) => s.trim()).filter(Boolean).map((sportName) => ({ sportName })),
      })),
    }
    try {
      await (editing ? updateTurf(id, body) : createTurf(body))
      navigate('/owner/turfs')
    } catch (err) {
      setError(err.status === 403 && !editing ? `${err.message} (New turfs need a verified owner account.)` : err)
      setBusy(false)
    }
  }

  return (
    <div style={{ maxWidth: 760 }}>
      <p><Link to="/owner/turfs">← My turfs</Link></p>
      <h1>{editing ? 'Edit turf' : 'Add a turf'}</h1>
      <ErrorMessage error={error} title="Could not save the turf" />
      <form onSubmit={submit} noValidate>
        <div className="panel">
          <Field label="Name" required value={f.name} onChange={(e) => setF({ ...f, name: e.target.value })} error={tried && errs.name} />
          <Field label="Location" required value={f.location} onChange={(e) => setF({ ...f, location: e.target.value })} error={tried && errs.location} hint="Customers search by this text (e.g. area or city)." />
          <Field label="Description">{(id2) => <textarea id={id2} rows={3} value={f.description} onChange={(e) => setF({ ...f, description: e.target.value })} />}</Field>
          <div className="row">
            <Field label="Opens" type="time" step="1800" required value={f.openingTime} onChange={(e) => setF({ ...f, openingTime: e.target.value })} error={tried && errs.openingTime} />
            <Field label="Closes" type="time" step="1800" required value={f.closingTime} onChange={(e) => setF({ ...f, closingTime: e.target.value })} error={tried && errs.closingTime} hint="Same time as opening means 24 hours. Times round to the nearest half hour." />
          </div>
        </div>
        <h2>Courts</h2>
        <datalist id="sports-list">{sportsQ.data?.map((s) => <option key={s.sportId} value={s.sportName} />)}</datalist>
        {courts.map((c, i) => (
          <div className="panel" key={i}>
            <div className="page-head" style={{ marginBottom: 8 }}>
              <h3>Court {editing ? c.courtNumber : i + 1}</h3>
              {!editing && courts.length > 1 && <button type="button" className="btn btn-secondary" onClick={() => setCourts(courts.filter((_, j) => j !== i))}>Remove</button>}
            </div>
            <div className="row">
              <Field label="Price per hour" type="number" min="1" required value={c.amountPerHour} onChange={setCourt(i, 'amountPerHour')} error={tried && courtErr[i].amountPerHour} />
              <Field label="Length" type="number" min="1" value={c.length} onChange={setCourt(i, 'length')} error={tried && courtErr[i].length} />
              <Field label="Width" type="number" min="1" value={c.width} onChange={setCourt(i, 'width')} error={tried && courtErr[i].width} />
            </div>
            <Field label="Sports" list="sports-list" value={c.sports} onChange={setCourt(i, 'sports')} hint="Comma-separated, e.g. Football, Cricket. Up to 10 per court." />
          </div>
        ))}
        {!editing && <Button type="button" variant="secondary" onClick={() => setCourts([...courts, blankCourt()])}>+ Add another court</Button>}
        {editing && <p className="muted small">Courts cannot be added or removed after creation; you can change their price, size and sports. Price changes apply to slots generated afterwards.</p>}
        <div style={{ marginTop: 20 }}><Button type="submit" loading={busy} className="btn-lg">{editing ? 'Save changes' : 'Create turf'}</Button></div>
      </form>
    </div>
  )
}
