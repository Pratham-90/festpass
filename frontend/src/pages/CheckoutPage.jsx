import { Navigate, useNavigate, useParams } from 'react-router'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '../api/client.js'
import { keys, useOrder } from '../api/queries.js'
import { useStomp } from '../realtime/useStomp.js'
import HoldTimer from '../components/HoldTimer.jsx'
import { Button, ButtonLink, Card, EmptyState, ErrorState, PageLoader, PageTitle } from '../components/ui.jsx'
import { useToast } from '../components/Toasts.jsx'
import { formatMoney } from '../lib/format.js'
import { serverNow } from '../lib/serverClock.js'

export function OrderSummary({ order }) {
  return (
    <Card>
      <h2 className="font-display text-lg font-bold">{order.eventTitle}</h2>
      <ul className="mt-4 flex flex-col gap-2 text-sm">
        {order.items.map((item, i) => (
          <li key={item.seat?.id ?? `${item.tierId}-${i}`} className="flex justify-between gap-4">
            <span>
              {item.seat ? `${item.seat.section} · Row ${item.seat.row} · Seat ${item.seat.number}` : `${item.quantity} × General admission`}
              <span className="text-ink-muted"> · {item.tierName}</span>
            </span>
            <span className="tabular-nums">{formatMoney(item.unitPricePaise * item.quantity)}</span>
          </li>
        ))}
      </ul>
      <div className="mt-4 flex justify-between border-t border-line pt-4 font-bold">
        <span>Total</span>
        <span className="tabular-nums">{formatMoney(order.amountPaise)}</span>
      </div>
    </Card>
  )
}

export function HoldExpired({ order }) {
  return (
    <EmptyState
      title="Your hold expired"
      description="The seats went back on sale and you weren't charged. They might still be free!"
      action={<ButtonLink to={order?.eventSlug ? `/events/${order.eventSlug}` : '/'}>Back to the event</ButtonLink>}
    />
  )
}

export default function CheckoutPage() {
  const { orderId } = useParams()
  const navigate = useNavigate()
  const toast = useToast()
  const queryClient = useQueryClient()
  const order = useOrder(orderId)

  // The server tells us the moment the hold ends (expired, or paid in another tab).
  useStomp('/user/queue/holds', (message) => {
    if (message.holdId === order.data?.holdId) queryClient.invalidateQueries({ queryKey: keys.order(orderId) })
  })

  const cancel = useMutation({
    mutationFn: () => api(`/holds/${order.data.holdId}`, { method: 'DELETE' }),
    onSuccess: () => {
      toast.push({ title: 'Seats released', description: 'They are back on sale.' })
      navigate(`/events/${order.data.eventSlug}`)
    },
    onError: toast.error,
  })

  if (order.isLoading) return <PageLoader />
  if (order.isError) return <ErrorState error={order.error} onRetry={order.refetch} />

  const o = order.data
  if (o.status === 'PAID') return <Navigate to={`/orders/${o.id}/confirmed`} replace />
  const expired = o.status === 'FAILED' || Date.parse(o.holdExpiresAt) <= serverNow()
  if (expired) return <HoldExpired order={o} />

  const totalSeconds = Math.max(60, Math.round((Date.parse(o.holdExpiresAt) - Date.parse(o.createdAt)) / 1000))

  return (
    <div className="mx-auto max-w-2xl">
      <PageTitle eyebrow="Checkout" title="Almost yours" />
      <div className="grid gap-6 sm:grid-cols-[auto_1fr] sm:items-start">
        <div className="flex flex-col items-center gap-2">
          <HoldTimer
            expiresAt={o.holdExpiresAt}
            totalSeconds={totalSeconds}
            onExpire={() => queryClient.invalidateQueries({ queryKey: keys.order(orderId) })}
          />
          <p className="max-w-40 text-center text-xs text-ink-muted">Pay before the timer runs out or the seats go back on sale.</p>
        </div>
        <div className="flex flex-col gap-4">
          <OrderSummary order={o} />
          <div className="flex flex-col gap-3 sm:flex-row">
            <Button className="flex-1" onClick={() => navigate(`/pay/${o.id}`)}>
              Pay {formatMoney(o.amountPaise)}
            </Button>
            <Button variant="ghost" loading={cancel.isPending} onClick={() => cancel.mutate()}>
              Release seats
            </Button>
          </div>
        </div>
      </div>
    </div>
  )
}
