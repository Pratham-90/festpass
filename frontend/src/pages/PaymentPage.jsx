import { useRef } from 'react'
import { Link, Navigate, useNavigate, useParams } from 'react-router'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '../api/client.js'
import { keys, useOrder } from '../api/queries.js'
import { Button, ErrorState, PageLoader } from '../components/ui.jsx'
import { useToast } from '../components/Toasts.jsx'
import { formatMoney } from '../lib/format.js'
import { newKey } from '../lib/ids.js'
import { HoldExpired } from './CheckoutPage.jsx'

/**
 * The mock payment gateway ("FestPay sandbox"). It looks deliberately unlike FestPass, the
 * way a real gateway page would. The server waits 0.5 to 2 s before answering.
 */
export default function PaymentPage() {
  const { orderId } = useParams()
  const navigate = useNavigate()
  const toast = useToast()
  const queryClient = useQueryClient()
  const order = useOrder(orderId)
  // One Idempotency-Key per attempt. A retry of the same attempt (e.g. after a network error) reuses it.
  const attemptKey = useRef(null)

  const pay = useMutation({
    mutationFn: (outcome) =>
      api(`/payments/mock/${orderId}`, { method: 'POST', body: { outcome }, headers: { 'Idempotency-Key': attemptKey.current } }),
    onSuccess: (paid) => {
      queryClient.setQueryData(keys.order(orderId), paid)
      queryClient.invalidateQueries({ queryKey: keys.tickets })
      navigate(`/orders/${orderId}/confirmed`, { replace: true })
    },
    onError: (error) => {
      if (error.code === 'HOLD_EXPIRED') queryClient.invalidateQueries({ queryKey: keys.order(orderId) })
      else toast.error(error)
    },
  })

  const submit = (outcome) => {
    if (!pay.isError || pay.error.status) attemptKey.current = newKey() // a fresh attempt unless retrying a network failure
    pay.mutate(outcome)
  }

  if (order.isLoading) return <GatewayShell><PageLoader /></GatewayShell>
  if (order.isError) return <GatewayShell><ErrorState error={order.error} onRetry={order.refetch} /></GatewayShell>
  const o = order.data
  if (o.status === 'PAID') return <Navigate to={`/orders/${o.id}/confirmed`} replace />
  if (o.status === 'FAILED') return <GatewayShell><HoldExpired order={o} /></GatewayShell>

  return (
    <GatewayShell>
      <div className="rounded-2xl bg-white p-6 text-slate-900 shadow-2xl">
        <p className="text-sm text-slate-500">Paying FestPass</p>
        <p className="mt-1 text-4xl font-black tabular-nums">{formatMoney(o.amountPaise)}</p>
        <p className="mt-1 text-sm text-slate-500">
          {o.eventTitle} · Order #{o.id}
        </p>

        <div className="mt-6 rounded-xl border border-slate-200 p-4 text-sm">
          <p className="font-semibold">Sandbox card</p>
          <p className="font-mono text-slate-500">4242 4242 4242 4242 · 12/30</p>
        </div>

        <div className="mt-6 flex flex-col gap-3">
          <Button className="bg-emerald-600 text-white shadow-none hover:bg-emerald-500" loading={pay.isPending && pay.variables === 'SUCCESS'}
                  disabled={pay.isPending} onClick={() => submit('SUCCESS')}>
            Pay {formatMoney(o.amountPaise)}
          </Button>
          <Button variant="ghost" className="text-slate-500 hover:bg-slate-100 hover:text-slate-900"
                  loading={pay.isPending && pay.variables === 'FAIL'} disabled={pay.isPending} onClick={() => submit('FAIL')}>
            Simulate a declined card
          </Button>
        </div>
        {pay.isPending && <p className="mt-4 text-center text-sm text-slate-500">Talking to the bank…</p>}
      </div>
      <p className="mt-4 text-center text-sm text-slate-400">
        <Link to={`/checkout/${o.id}`} className="underline">Back to FestPass</Link>
      </p>
    </GatewayShell>
  )
}

function GatewayShell({ children }) {
  return (
    <div className="min-h-dvh bg-slate-800 px-4 py-10">
      <div className="mx-auto max-w-sm">
        <p className="mb-6 text-center text-lg font-bold text-white">
          FestPay <span className="rounded bg-amber-400 px-1.5 py-0.5 text-xs text-slate-900">SANDBOX</span>
        </p>
        {children}
      </div>
    </div>
  )
}
