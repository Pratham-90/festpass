import { useEffect, useRef } from 'react'
import { Navigate, useParams } from 'react-router'
import { motion } from 'framer-motion'
import { useMyTickets, useOrder } from '../api/queries.js'
import TicketFlipCard from '../components/TicketFlipCard.jsx'
import { ButtonLink, ErrorState, PageLoader } from '../components/ui.jsx'
import { celebrate } from '../lib/celebrate.js'

/** The ticket reveal: confetti in the tier's colour, then the tickets flip over to show their QR codes. */
export default function OrderConfirmedPage() {
  const { orderId } = useParams()
  const order = useOrder(orderId)
  const tickets = useMyTickets()
  const celebrated = useRef(false)

  const paid = order.data?.status === 'PAID'
  const mine = paid && tickets.data ? tickets.data.filter((t) => t.orderId === order.data.id) : []

  useEffect(() => {
    if (mine.length && !celebrated.current) {
      celebrated.current = true
      celebrate([...new Set(mine.map((t) => t.tierColor))])
    }
  }, [mine])

  if (order.isLoading || tickets.isLoading) return <PageLoader />
  if (order.isError) return <ErrorState error={order.error} onRetry={order.refetch} />
  if (tickets.isError) return <ErrorState error={tickets.error} onRetry={tickets.refetch} />
  if (!paid) return <Navigate to={`/checkout/${order.data.id}`} replace />

  return (
    <div className="mx-auto max-w-3xl">
      <motion.div
        initial={{ scale: 0.8, opacity: 0 }}
        animate={{ scale: 1, opacity: 1 }}
        transition={{ type: 'spring', stiffness: 200, damping: 14 }}
        className="mb-8 text-center"
      >
        <div className="text-6xl" aria-hidden>🎉</div>
        <h1 className="font-display mt-3 text-4xl font-black sm:text-5xl">You're going!</h1>
        <p className="mt-2 text-ink-muted">
          {order.data.eventTitle}. Your tickets are in your wallet, and a copy is on its way to your email.
        </p>
      </motion.div>

      <div className={`grid gap-5 ${mine.length > 1 ? 'sm:grid-cols-2' : 'mx-auto max-w-sm'}`}>
        {mine.map((ticket, i) => (
          <motion.div
            key={ticket.id}
            initial={{ opacity: 0, y: 40 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ delay: 0.3 + i * 0.15 }}
          >
            <TicketFlipCard ticket={ticket} revealAfter={1400 + i * 300} />
          </motion.div>
        ))}
      </div>

      <div className="mt-8 flex flex-wrap justify-center gap-3">
        <ButtonLink to="/tickets">Open my wallet</ButtonLink>
        <ButtonLink to="/passport" variant="secondary">My Fest Passport</ButtonLink>
        <ButtonLink to="/" variant="secondary">Find more events</ButtonLink>
      </div>
    </div>
  )
}
