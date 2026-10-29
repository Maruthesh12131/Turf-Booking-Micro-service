import { Route, Routes } from 'react-router-dom'
import Layout from './components/Layout'
import RequireRole from './components/RequireRole'
import Account from './pages/Account'
import AdminDashboard from './pages/admin/AdminDashboard'
import BookingConfirm from './pages/BookingConfirm'
import BookingDetail from './pages/BookingDetail'
import Home from './pages/Home'
import Login from './pages/Login'
import MyBookings from './pages/MyBookings'
import NotFound from './pages/NotFound'
import OwnerBookings from './pages/owner/OwnerBookings'
import OwnerDashboard from './pages/owner/OwnerDashboard'
import OwnerProfile from './pages/owner/OwnerProfile'
import OwnerSlots from './pages/owner/OwnerSlots'
import OwnerTurfs from './pages/owner/OwnerTurfs'
import TurfForm from './pages/owner/TurfForm'
import Register from './pages/Register'
import Slots from './pages/Slots'
import TurfDetails from './pages/TurfDetails'

const guard = (roles, el) => <RequireRole roles={roles}>{el}</RequireRole>

export default function App() {
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route path="/" element={<Home />} />
        <Route path="/login" element={<Login />} />
        <Route path="/register" element={<Register />} />
        <Route path="/turfs/:id" element={<TurfDetails />} />
        <Route path="/turfs/:id/slots" element={<Slots />} />
        <Route path="/book" element={guard(['CUSTOMER'], <BookingConfirm />)} />
        <Route path="/bookings" element={guard(['CUSTOMER'], <MyBookings />)} />
        <Route path="/bookings/:id" element={guard(['CUSTOMER'], <BookingDetail />)} />
        <Route path="/account" element={guard(['CUSTOMER', 'OWNER', 'ADMIN'], <Account />)} />
        <Route path="/owner" element={guard(['OWNER'], <OwnerDashboard />)} />
        <Route path="/owner/profile" element={guard(['OWNER'], <OwnerProfile />)} />
        <Route path="/owner/turfs" element={guard(['OWNER'], <OwnerTurfs />)} />
        <Route path="/owner/turfs/new" element={guard(['OWNER'], <TurfForm />)} />
        <Route path="/owner/turfs/:id/edit" element={guard(['OWNER'], <TurfForm />)} />
        <Route path="/owner/turfs/:id/slots" element={guard(['OWNER'], <OwnerSlots />)} />
        <Route path="/owner/bookings" element={guard(['OWNER'], <OwnerBookings />)} />
        <Route path="/admin" element={guard(['ADMIN'], <AdminDashboard />)} />
        <Route path="*" element={<NotFound />} />
      </Route>
    </Routes>
  )
}
