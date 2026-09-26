import { useEffect, useState } from 'react'
import { Angry, Frown, Laugh, Meh, Smile } from 'lucide-react'
import { api, rupees } from '../api.js'
import CoffeeCup from '../ui/CoffeeCup.jsx'
import { ErrorNote, Spinner } from '../ui/bits.jsx'

const STEPS = [
  { status: 'PLACED', label: 'Received' },
  { status: 'PREPARING', label: 'Brewing' },
  { status: 'READY', label: 'Ready' },
]
const LEVEL = { PLACED: 0.18, PREPARING: 0.6, READY: 0.92, COLLECTED: 0.92, CANCELLED: 0 }

const HEADLINE = {
  PLACED: 'We’ve got your order',
  PREPARING: 'Your order is being made',
  READY: 'Ready at the counter',
  COLLECTED: 'Enjoy!',
  CANCELLED: 'This order was cancelled',
}

export default function OrderTracker({ session, orderId, onOrderAgain }) {
  const [view, setView] = useState(null)
  const [error, setError] = useState('')
  const [live, setLive] = useState(false)

  useEffect(() => {
    if (!orderId) return
    let es
    let closed = false
    api.order(orderId, session.id).then(setView).catch((e) => setError(e.message))
    function connect() {
      es = new EventSource(`/api/orders/${orderId}/stream?sessionId=${encodeURIComponent(session.id)}`)
      es.addEventListener('order', (e) => { setView(JSON.parse(e.data)); setLive(true) })
      es.onerror = () => {
        setLive(false)
        if (es.readyState === EventSource.CLOSED && !closed) setTimeout(connect, 3000)
      }
    }
    connect()
    return () => { closed = true; es?.close() }
  }, [orderId, session.id])

  if (!orderId) {
    return (
      <div className="rounded-3xl bg-white p-8 text-center">
        <p className="font-display text-xl font-bold">No active order</p>
        <p className="mt-1 text-muted">Place an order and you can follow it here.</p>
      </div>
    )
  }
  if (error && !view) return <ErrorNote message={error} />
  if (!view) return <div className="flex justify-center py-16 text-muted"><Spinner /></div>

  const { order, etaMinutes, queuePosition } = view
  const status = order.status
  const stepIndex = STEPS.findIndex((s) => s.status === status)
  const done = status === 'COLLECTED' || status === 'CANCELLED'

  return (
    <div className="space-y-5 pb-6">
      <section className="rounded-3xl bg-white p-6 text-center">
        <p className="text-sm font-medium text-muted">Pickup code</p>
        <p className="font-display text-6xl font-extrabold tracking-tight">#{order.code}</p>
        <CoffeeCup level={LEVEL[status] ?? 0} steaming={status === 'READY'} />
        <h2 className="font-display text-2xl font-bold" aria-live="polite">{HEADLINE[status]}</h2>
        <p className="mt-1 text-muted">
          {status === 'READY' && `Collect from the counter, ${order.customerName}.`}
          {(status === 'PLACED' || status === 'PREPARING') &&
            `About ${etaMinutes} min${queuePosition > 1 ? `, ${queuePosition - 1} ${queuePosition - 1 === 1 ? 'order' : 'orders'} ahead of you` : ''}.`}
          {done && 'Thanks for visiting Google Coffee.'}
        </p>

        {status !== 'CANCELLED' && (
          <ol className="mt-6 grid grid-cols-3 gap-2" aria-label="Order progress">
            {STEPS.map((s, i) => {
              const reached = done || i <= stepIndex
              return (
                <li key={s.status} className="text-center">
                  <div className={`h-1.5 rounded-full ${reached ? 'bg-leaf' : 'bg-line'}`} />
                  <span className={`mt-2 block text-xs font-semibold ${reached ? 'text-leaf' : 'text-muted'}`}>{s.label}</span>
                </li>
              )
            })}
          </ol>
        )}
        <p className="mt-4 flex items-center justify-center gap-1.5 text-xs text-muted">
          <span className={`inline-block h-2 w-2 rounded-full ${live ? 'anim-live bg-leaf' : 'bg-line'}`} />
          {live ? 'Live updates on' : 'Reconnecting'}
        </p>
      </section>

      <section className="rounded-3xl bg-white p-5">
        <h3 className="font-semibold">Order details</h3>
        <ul className="mt-2 space-y-1 text-sm">
          {order.items.map((i) => (
            <li key={i.itemId} className="flex justify-between gap-2">
              <span>{i.qty} × {i.name}{i.note ? <span className="text-muted">, {i.note}</span> : null}</span>
              <span>{rupees(i.unitPrice * i.qty)}</span>
            </li>
          ))}
        </ul>
        <div className="mt-3 flex justify-between border-t border-line pt-3 font-semibold">
          <span>Total, pay at counter</span><span>{rupees(order.total)}</span>
        </div>
      </section>

      <FeedbackCard session={session} orderId={order.id} />

      {done && (
        <button onClick={onOrderAgain} className="w-full rounded-2xl bg-roast py-4 font-semibold text-steam hover:bg-roast-soft">
          Order something else
        </button>
      )}
    </div>
  )
}

const FACES = [
  { rating: 1, Icon: Angry, label: 'Very unhappy' },
  { rating: 2, Icon: Frown, label: 'Unhappy' },
  { rating: 3, Icon: Meh, label: 'Okay' },
  { rating: 4, Icon: Smile, label: 'Happy' },
  { rating: 5, Icon: Laugh, label: 'Loved it' },
]

function FeedbackCard({ session, orderId }) {
  const [rating, setRating] = useState(0)
  const [comment, setComment] = useState('')
  const [state, setState] = useState('idle')
  const [error, setError] = useState('')

  async function submit() {
    if (!rating) return setError('Pick a face first.')
    setState('sending')
    setError('')
    try {
      await api.feedback(session.id, orderId, rating, comment.trim())
      setState('sent')
    } catch (e) {
      setError(e.message)
      setState('idle')
    }
  }

  if (state === 'sent') {
    return (
      <section className="rounded-3xl bg-leaf p-5 text-steam">
        <p className="font-semibold">Thanks, the team sees this right away.</p>
        <p className="text-sm text-steam/80">Your feedback helps them adjust the room in real time.</p>
      </section>
    )
  }

  return (
    <section className="rounded-3xl bg-white p-5">
      <h3 className="font-semibold">How’s your visit so far?</h3>
      <div className="mt-3 flex justify-between" role="radiogroup" aria-label="Rating">
        {FACES.map(({ rating: r, Icon, label }) => (
          <button key={r} role="radio" aria-checked={rating === r} aria-label={label} onClick={() => { setRating(r); setError('') }}
            className={`grid h-12 w-12 place-items-center rounded-full transition ${rating === r ? 'bg-crema text-roast' : 'bg-steam text-muted hover:text-roast'}`}>
            <Icon size={24} strokeWidth={1.8} />
          </button>
        ))}
      </div>
      <textarea value={comment} onChange={(e) => setComment(e.target.value)} maxLength={300} rows={2}
        placeholder="Music, temperature, wifi, your drink… anything" aria-label="Comment"
        className="mt-4 w-full resize-none rounded-xl bg-steam px-3 py-2.5 text-sm outline-none focus:ring-1 focus:ring-leaf" />
      {error && <p className="mt-2 text-sm text-cherry">{error}</p>}
      <button onClick={submit} disabled={state === 'sending'}
        className="mt-3 flex items-center gap-2 rounded-full bg-leaf px-5 py-2.5 text-sm font-semibold text-steam disabled:opacity-60">
        {state === 'sending' && <Spinner />} Send feedback
      </button>
    </section>
  )
}
