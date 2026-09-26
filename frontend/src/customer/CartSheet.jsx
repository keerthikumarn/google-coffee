import { useEffect, useState } from 'react'
import { Clock, Minus, Plus, X } from 'lucide-react'
import { api, rupees } from '../api.js'
import { ErrorNote, Spinner } from '../ui/bits.jsx'

export default function CartSheet({ cart, setQty, setNote, onClose, onPlace, placing, error }) {
  const [eta, setEta] = useState(null)
  const total = cart.reduce((s, l) => s + l.item.price * l.qty, 0)
  const signature = cart.map((l) => `${l.item.id}:${l.qty}`).join(',')

  useEffect(() => {
    if (!cart.length) return
    const t = setTimeout(() => {
      api.estimate(cart.map((l) => ({ itemId: l.item.id, qty: l.qty })))
        .then((r) => setEta(r.minutes))
        .catch(() => setEta(null))
    }, 300)
    return () => clearTimeout(t)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [signature])

  return (
    <div className="fixed inset-0 z-40 flex items-end justify-center bg-roast/40" onClick={onClose}>
      <section role="dialog" aria-modal="true" aria-label="Your order" onClick={(e) => e.stopPropagation()}
        className="anim-rise max-h-[88vh] w-full max-w-md overflow-y-auto rounded-t-[28px] bg-steam p-5 pb-8">
        <div className="flex items-center justify-between">
          <h2 className="font-display text-2xl font-bold">Your order</h2>
          <button onClick={onClose} aria-label="Close" className="grid h-10 w-10 place-items-center rounded-full bg-white"><X size={18} /></button>
        </div>

        {cart.length === 0 ? (
          <p className="py-10 text-center text-muted">Nothing here yet. Add something from the menu or ask Brew.</p>
        ) : (
          <>
            <ul className="mt-4 space-y-3">
              {cart.map((l) => (
                <li key={l.item.id} className="rounded-2xl bg-white p-4">
                  <div className="flex items-center justify-between gap-3">
                    <div>
                      <p className="font-semibold">{l.item.name}</p>
                      <p className="text-sm text-muted">{rupees(l.item.price)} each</p>
                    </div>
                    <div className="flex items-center gap-2">
                      <button onClick={() => setQty(l.item.id, l.qty - 1)} aria-label={`Remove one ${l.item.name}`}
                        className="grid h-9 w-9 place-items-center rounded-full border border-line"><Minus size={15} /></button>
                      <span className="w-5 text-center font-semibold" aria-live="polite">{l.qty}</span>
                      <button onClick={() => setQty(l.item.id, l.qty + 1)} disabled={l.qty >= 10} aria-label={`Add one ${l.item.name}`}
                        className="grid h-9 w-9 place-items-center rounded-full border border-line disabled:opacity-40"><Plus size={15} /></button>
                    </div>
                  </div>
                  <input value={l.note} onChange={(e) => setNote(l.item.id, e.target.value)} maxLength={80}
                    placeholder="Note for the barista (e.g. extra hot)" aria-label={`Note for ${l.item.name}`}
                    className="mt-3 w-full rounded-lg bg-steam px-3 py-2 text-sm outline-none focus:ring-1 focus:ring-leaf" />
                </li>
              ))}
            </ul>

            <div className="mt-5 flex items-center gap-3 rounded-2xl bg-crema-soft p-4">
              <Clock size={20} className="text-roast" aria-hidden="true" />
              <p className="text-sm">
                {eta ? <>Ready in about <strong>{eta} min</strong> if you order now, based on the current queue.</> : 'Checking the queue…'}
              </p>
            </div>

            <div className="mt-5 flex items-baseline justify-between">
              <span className="text-muted">Total, pay at counter</span>
              <span className="font-display text-2xl font-bold">{rupees(total)}</span>
            </div>

            <div className="mt-4"><ErrorNote message={error} /></div>

            <button onClick={onPlace} disabled={placing}
              className="mt-4 flex w-full items-center justify-center gap-2 rounded-2xl bg-roast py-4 font-semibold text-steam hover:bg-roast-soft disabled:opacity-60">
              {placing && <Spinner />} Place order
            </button>
          </>
        )}
      </section>
    </div>
  )
}
