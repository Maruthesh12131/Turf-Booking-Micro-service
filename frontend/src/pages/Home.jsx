import { useSearchParams } from 'react-router-dom'
import TurfCard, { TurfCardSkeleton } from '../components/TurfCard'
import { EmptyState, ErrorMessage, Pagination } from '../components/ui'
import { useAuth } from '../context/AuthContext'
import { useAsync } from '../hooks/useAsync'
import { searchTurfs } from '../services/turfs'
import { Navigate } from 'react-router-dom'
import { homePathFor } from '../context/AuthContext'

const PAGE_SIZE = 12

export default function Home() {
  const { role } = useAuth()
  const [params, setParams] = useSearchParams()
  const location = params.get('location') || ''
  const page = Math.max(0, Number(params.get('page')) || 0)
  const { data, error, loading, reload } = useAsync((signal) => searchTurfs({ location, page, size: PAGE_SIZE }, signal), [location, page])

  if (role && role !== 'CUSTOMER') return <Navigate to={homePathFor(role)} replace />

  const setPage = (p) => {
    const next = new URLSearchParams(params)
    next.set('page', p)
    setParams(next)
    window.scrollTo({ top: 0 })
  }

  return (
    <>
      <div className="page-head">
        <div>
          <h1>{location ? `Turfs matching “${location}”` : 'Find a turf'}</h1>
          {data && <p className="muted">{data.totalElements} turf{data.totalElements === 1 ? '' : 's'} {location ? 'found' : 'available'}</p>}
        </div>
        {location && <button className="btn btn-secondary" onClick={() => setParams({})}>Clear search</button>}
      </div>
      <ErrorMessage error={error} onRetry={reload} title="Could not load turfs" />
      {loading && <div className="grid">{Array.from({ length: 8 }, (_, i) => <TurfCardSkeleton key={i} />)}</div>}
      {data && data.content.length === 0 && (
        <EmptyState title={location ? 'No turfs match that location' : 'No turfs yet'}>
          {location ? 'Try a different area or clear the search.' : 'Turfs appear here once an owner lists them.'}
        </EmptyState>
      )}
      {data && data.content.length > 0 && !loading && (
        <>
          <div className="grid">{data.content.map((t) => <TurfCard key={t.turfId} turf={t} />)}</div>
          <Pagination page={data.page} totalPages={data.totalPages} onChange={setPage} />
        </>
      )}
    </>
  )
}
