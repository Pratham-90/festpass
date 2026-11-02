import { Navigate, useParams } from 'react-router'
import { motion } from 'framer-motion'
import { useMyTickets, useOrder } from '../api/queries.js'
import { ButtonLink, ErrorState, PageLoader } from '../components/ui.jsx'
import { TicketCard } from './WalletPage.jsx'

/** After payment. Phase 5 adds confetti in the tier colour and the 3D ticket flip here. */
export default function OrderConfirmedPage() {
  const { orderId } = useParams()
  const order = useOrder(orderId)
  const tickets = useMyTickets()

  if (order.isLoading || tickets.isLoading) return <PageLoader />
  if (order.isError) return <ErrorState error={order.error} onRetry={order.refetch} />
  if (tickets.isError) return <ErrorState error={tickets.error} onRetry={tickets.refetch} />

  if (order.data.status !== 'PAID') return <Navigate to={`/checkout/${order.data.id}`} replace />
  const mine = tickets.data.filter((t) => t.orderId === order.data.id)

  return (
    <div className="mx-auto max-w-2xl">
      <motion.div initial={{ scale: 0.9, opacity: 0 }} animate={{ scale: 1, opacity: 1 }} className="mb-8 text-center">
        <div className="text-6xl" aria-hidden>🎉</div>
        <h1 className="font-display mt-3 text-4xl font-black">You're going!</h1>
        <p className="mt-2 text-ink-muted">
          {order.data.eventTitle}. Your tickets are in your wallet, and a copy is on its way to your email.
        </p>
      </motion.div>
      <div className="flex flex-col gap-3">
        {mine.map((ticket, i) => (
          <TicketCard key={ticket.id} ticket={ticket} index={i} />
        ))}
      </div>
      <div className="mt-8 flex justify-center gap-3">
        <ButtonLink to="/tickets">Open my wallet</ButtonLink>
        <ButtonLink to="/" variant="secondary">Find more events</ButtonLink>
      </div>
    </div>
  )
}
