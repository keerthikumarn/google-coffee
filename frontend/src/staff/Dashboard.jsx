import { useEffect, useState } from 'react'
import { LogOut, Minus, Plus, Users } from 'lucide-react'
import { api, rupees } from '../api.js'
import { Spinner } from '../ui/bits.jsx'
import PulsePanel from './PulsePanel.jsx'

const COLUMNS = [
  { status: 'PLACED', title: 'New', next: 'PREPARING', action: 'Start' },
  { status: 'PREPARING', title: 'Brewing', next: 'READY', action: 'Mark ready' },
  { status: 'READY', title: 'Ready for pickup', next: 'COLLECTED', action: 'Collected' },
]

export default function Dashboard({ token, onSignOut }) {
  const [board, setBoard] = useState(null)
  const [live, setLive] = useState(false)
  const [error, setError] = useState('')
  const [pending, setPending] = useState({})
  const [now, setNow] = useState(Date.now())

  useEffect(() => {
    let es
    let closed = false
    function connect() {
      es = new EventSource(`/api/staff/stream?token=${encodeURIComponent(token)}`)
      es.addEventListener('board', (e) => { setBoard(JSON.parse(e.data)); setLive(true); setError('') })
      es.onerror = () => {
        setLive(false)
        if (es.readyState === EventSource.CLOSED && !closed) {
          // A closed stream usually means the token expired; verify with a cheap authenticated call.
          api.pulse(token).then(() => setTimeout(connect, 3000)).catch((err) => {
            if (err.status === 401) onSignOut()
            else setTimeout(connect, 3000)
          })
        }
      }
    }
    connect()
    const tick = setInterval(() => setNow(Date.now()), 30000)
    return () => { closed = true; es?.close(); clearInterval(tick) }
  }, [token, onSignOut])

  async function advance(order, next) {
    setPending((p) => ({ ...p, [order.id]: true }))
    setError('')
    try {
      await api.setStatus(token, order.id, next)
    } catch (e) {
      if (e.status === 401) return onSignOut()
      setError(e.message)
    } finally {
      setPending((p) => ({ ...p, [order.id]: false }))
    }
  }

  async function changeBaristas(delta) {
    const n = Math.max(1, Math.min(8, (board?.activeBaristas || 1) + delta))
    try {
      await api.setBaristas(token, n)
    } catch (e) {
      if (e.status === 401) return onSignOut()
      setError(e.message)
    }
  }

  const orders = board?.orders || []

  return (
    <div className="min-h-screen bg-steam">
      <header className="flex flex-wrap items-center justify-between gap-4 bg-roast px-6 py-4 text-steam">
        <div className="flex items-center gap-4">
          <p className="font-display text-2xl font-extrabold tracking-tight">Google Coffee</p>
          <span className="flex items-center gap-1.5 rounded-full bg-steam/10 px-3 py-1 text-xs">
            <span className={`inline-block h-2 w-2 rounded-full ${live ? 'anim-live bg-crema' : 'bg-steam/40'}`} />
            {live ? 'Live' : 'Connecting'}
          </span>
        </div>
        <div className="flex items-center gap-3">
          <div className="flex items-center gap-2 rounded-full bg-steam/10 py-1 pl-3 pr-1">
            <Users size={16} aria-hidden="true" />
            <span className="text-sm">Baristas on bar</span>
            <button onClick={() => changeBaristas(-1)} aria-label="Fewer baristas" className="grid h-7 w-7 place-items-center rounded-full bg-steam/15 hover:bg-steam/25"><Minus size={14} /></button>
            <span className="w-4 text-center font-semibold" aria-live="polite">{board?.activeBaristas ?? '–'}</span>
            <button onClick={() => changeBaristas(1)} aria-label="More baristas" className="grid h-7 w-7 place-items-center rounded-full bg-steam/15 hover:bg-steam/25"><Plus size={14} /></button>
          </div>
          <button onClick={onSignOut} className="flex items-center gap-1.5 rounded-full px-3 py-1.5 text-sm text-steam/80 hover:bg-steam/10">
            <LogOut size={15} aria-hidden="true" /> Sign out
          </button>
        </div>
      </header>

      {error && <div role="alert" className="bg-cherry px-6 py-2 text-sm text-white">{error}</div>}

      <div className="grid gap-6 p-6 xl:grid-cols-[1fr_380px]">
        <div className="grid gap-4 md:grid-cols-3">
          {COLUMNS.map((col) => {
            const list = orders.filter((v) => v.order.status === col.status)
            return (
              <section key={col.status} aria-label={col.title} className="min-h-40 rounded-3xl bg-white/60 p-3">
                <h2 className="flex items-baseline justify-between px-2 pb-3 pt-1">
                  <span className="font-display text-lg font-bold">{col.title}</span>
                  <span className="text-sm text-muted">{list.length}</span>
                </h2>
                {!board && <div className="flex justify-center py-8 text-muted"><Spinner /></div>}
                {board && list.length === 0 && <p className="px-2 py-6 text-sm text-muted">Nothing here right now.</p>}
                <div className="space-y-3">
                  {list.map((v) => (
                    <OrderCard key={v.order.id} view={v} now={now} col={col} busy={!!pending[v.order.id]}
                      onAdvance={() => advance(v.order, col.next)}
                      onCancel={col.status !== 'READY' ? () => advance(v.order, 'CANCELLED') : null} />
                  ))}
                </div>
              </section>
            )
          })}
        </div>
        <PulsePanel token={token} onUnauthorized={onSignOut} />
      </div>
    </div>
  )
}

function OrderCard({ view, now, col, busy, onAdvance, onCancel }) {
  const { order, etaMinutes } = view
  const ageMin = Math.max(0, Math.floor((now - order.createdAt) / 60000))
  const late = order.status !== 'READY' && ageMin > order.prepMinutes + 5
  return (
    <article className={`anim-rise rounded-2xl border bg-white p-4 ${late ? 'border-cherry' : 'border-line'}`}>
      <div className="flex items-start justify-between gap-2">
        <div>
          <p className="font-display text-2xl font-extrabold leading-none">#{order.code}</p>
          <p className="mt-1 text-sm text-muted">{order.customerName}, table {order.table}</p>
        </div>
        <div className="text-right text-xs">
          <p className={late ? 'font-semibold text-cherry' : 'text-muted'}>{ageMin === 0 ? 'just now' : `${ageMin} min ago`}</p>
          {order.status !== 'READY' && <p className="text-muted">ETA {etaMinutes} min</p>}
        </div>
      </div>
      <ul className="mt-3 space-y-1 text-sm">
        {order.items.map((i) => (
          <li key={i.itemId}>
            <span className="font-semibold">{i.qty}×</span> {i.name}
            {i.note && <span className="block pl-5 text-xs text-cherry">{i.note}</span>}
          </li>
        ))}
      </ul>
      <div className="mt-3 flex items-center justify-between gap-2">
        <span className="text-sm text-muted">{rupees(order.total)}</span>
        <div className="flex gap-2">
          {onCancel && (
            <button onClick={onCancel} disabled={busy} className="rounded-full px-3 py-2 text-xs text-muted hover:text-cherry disabled:opacity-50">Cancel</button>
          )}
          <button onClick={onAdvance} disabled={busy}
            className="flex items-center gap-1.5 rounded-full bg-leaf px-4 py-2 text-sm font-semibold text-steam hover:bg-roast disabled:opacity-60">
            {busy && <Spinner />} {col.action}
          </button>
        </div>
      </div>
    </article>
  )
}
