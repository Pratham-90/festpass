import { lazy, Suspense } from 'react'
import { Route, Routes } from 'react-router'
import Layout from './components/Layout.jsx'
import RequireAuth from './auth/RequireAuth.jsx'
import DiscoverPage from './pages/DiscoverPage.jsx'
import { LoginPage, RegisterPage } from './pages/AuthPages.jsx'
import EventPage from './pages/EventPage.jsx'
import CheckoutPage from './pages/CheckoutPage.jsx'
import PaymentPage from './pages/PaymentPage.jsx'
import OrderConfirmedPage from './pages/OrderConfirmedPage.jsx'
import WalletPage from './pages/WalletPage.jsx'
import TicketPage from './pages/TicketPage.jsx'
import OrganizerEventsPage from './pages/organizer/OrganizerEventsPage.jsx'
import NewEventPage from './pages/organizer/NewEventPage.jsx'
import EventEditorPage from './pages/organizer/EventEditorPage.jsx'
import AdminUsersPage from './pages/admin/AdminUsersPage.jsx'
import PassportPage from './pages/PassportPage.jsx'
import GatePage from './pages/gate/GatePage.jsx'
import NotFoundPage from './pages/NotFoundPage.jsx'
import { PageLoader } from './components/ui.jsx'

// Pages with heavy libraries (charts, the QR scanner) are only downloaded when someone opens them.
const DashboardPage = lazy(() => import('./pages/organizer/DashboardPage.jsx'))
const ScannerPage = lazy(() => import('./pages/gate/ScannerPage.jsx'))

const ORGANIZERS = ['ORGANIZER', 'ADMIN']
const GATE = ['GATE_STAFF', 'ADMIN']

export default function App() {
  return (
    <Suspense fallback={<PageLoader />}>
    <Routes>
      {/* The mock payment page is deliberately outside the FestPass layout: it plays an external gateway. */}
      <Route path="/pay/:orderId" element={<RequireAuth><PaymentPage /></RequireAuth>} />
      {/* The scanner is full-screen on a phone, so it has no site header either. */}
      <Route path="/gate/:eventId" element={<RequireAuth roles={GATE}><ScannerPage /></RequireAuth>} />

      <Route element={<Layout />}>
        <Route index element={<DiscoverPage />} />
        <Route path="login" element={<LoginPage />} />
        <Route path="register" element={<RegisterPage />} />
        <Route path="events/:slug" element={<EventPage />} />

        <Route path="checkout/:orderId" element={<RequireAuth><CheckoutPage /></RequireAuth>} />
        <Route path="orders/:orderId/confirmed" element={<RequireAuth><OrderConfirmedPage /></RequireAuth>} />
        <Route path="tickets" element={<RequireAuth><WalletPage /></RequireAuth>} />
        <Route path="tickets/:ticketId" element={<RequireAuth><TicketPage /></RequireAuth>} />
        <Route path="passport" element={<RequireAuth><PassportPage /></RequireAuth>} />
        <Route path="gate" element={<RequireAuth roles={GATE}><GatePage /></RequireAuth>} />

        <Route path="organizer" element={<RequireAuth roles={ORGANIZERS}><OrganizerEventsPage /></RequireAuth>} />
        <Route path="organizer/events/new" element={<RequireAuth roles={ORGANIZERS}><NewEventPage /></RequireAuth>} />
        <Route path="organizer/events/:eventId" element={<RequireAuth roles={ORGANIZERS}><EventEditorPage /></RequireAuth>} />
        <Route path="organizer/events/:eventId/dashboard" element={<RequireAuth roles={ORGANIZERS}><DashboardPage /></RequireAuth>} />

        <Route path="admin/users" element={<RequireAuth roles={['ADMIN']}><AdminUsersPage /></RequireAuth>} />

        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
    </Suspense>
  )
}
