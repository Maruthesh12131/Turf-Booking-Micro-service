import { Link } from 'react-router-dom'
import { EmptyState } from '../components/ui'

export default () => <EmptyState title="Page not found"><Link to="/">Go to the home page</Link></EmptyState>
