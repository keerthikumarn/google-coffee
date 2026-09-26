import { useEffect, useMemo, useState } from 'react'
import { Plus, Sparkles } from 'lucide-react'
import { api, rupees } from '../api.js'
import { ItemGlyph, Spinner, Tags } from '../ui/bits.jsx'

const CATEGORIES = ['Espresso bar', 'Cold coffee', 'Not coffee', 'Bakery & bites']

export default function MenuTab({ session, menu, onAdd, cartQty }) {
  const [category, setCategory] = useState('All')
  const [picks, setPicks] = useState(null)
  const [picksError, setPicksError] = useState(false)

  useEffect(() => {
    let cancelled = false
    api.recommendations(session.id)
      .then((r) => !cancelled && setPicks(r))
      .catch(() => !cancelled && setPicksError(true))
    return () => { cancelled = true }
  }, [session.id])

  const visible = useMemo(
    () => (category === 'All' ? menu : menu.filter((m) => m.category === category)),
    [menu, category],
  )

  return (
    <div className="space-y-7 pb-4">
      <section aria-label="Picked for you" className="rounded-3xl bg-roast p-5 text-steam">
        <div className="flex items-center gap-2 text-crema">
          <Sparkles size={16} aria-hidden="true" />
          <span className="text-sm font-medium">Picked by Brew for {session.name}</span>
        </div>
        {!picks && !picksError && (
          <div className="flex items-center gap-2 py-6 text-sm text-steam/70"><Spinner /> Reading the menu for you</div>
        )}
        {picksError && <p className="py-4 text-sm text-steam/70">Recommendations are unavailable right now. The full menu is below.</p>}
        {picks && (
          <>
            <h2 className="mt-1 font-display text-2xl font-bold leading-tight">{picks.headline}</h2>
            <div className="no-scrollbar -mx-5 mt-4 flex gap-3 overflow-x-auto px-5 pb-1">
              {picks.picks.map(({ item, reason }) => (
                <article key={item.id} className="anim-rise w-56 shrink-0 rounded-2xl bg-steam p-4 text-roast">
                  <ItemGlyph category={item.category} />
                  <h3 className="mt-3 font-semibold leading-snug">{item.name}</h3>
                  <p className="mt-1 text-sm leading-snug text-muted">{reason}</p>
                  <div className="mt-3 flex items-center justify-between">
                    <span className="font-semibold">{rupees(item.price)}</span>
                    <button onClick={() => onAdd(item)} aria-label={`Add ${item.name}`}
                      className="grid h-9 w-9 place-items-center rounded-full bg-leaf text-steam hover:bg-roast">
                      <Plus size={18} />
                    </button>
                  </div>
                </article>
              ))}
            </div>
          </>
        )}
      </section>

      <section aria-label="Menu">
        <div className="no-scrollbar -mx-5 flex gap-2 overflow-x-auto px-5">
          {['All', ...CATEGORIES].map((c) => (
            <button key={c} onClick={() => setCategory(c)} aria-pressed={category === c}
              className={`shrink-0 rounded-full px-4 py-2 text-sm font-medium ${category === c ? 'bg-roast text-steam' : 'bg-white text-roast'}`}>
              {c}
            </button>
          ))}
        </div>

        <ul className="mt-4 divide-y divide-line rounded-3xl bg-white">
          {visible.map((item) => (
            <li key={item.id} className="flex gap-4 p-4">
              <ItemGlyph category={item.category} />
              <div className="min-w-0 flex-1">
                <div className="flex items-baseline justify-between gap-3">
                  <h3 className="font-semibold">{item.name}</h3>
                  <span className="shrink-0 font-semibold">{rupees(item.price)}</span>
                </div>
                <p className="mt-0.5 text-sm leading-snug text-muted">{item.description}</p>
                <Tags tags={item.tags} />
              </div>
              <button onClick={() => onAdd(item)} aria-label={`Add ${item.name}`}
                className="relative grid h-10 w-10 shrink-0 place-items-center self-center rounded-full border border-line text-leaf hover:border-leaf">
                <Plus size={18} />
                {cartQty(item.id) > 0 && (
                  <span className="absolute -right-1 -top-1 grid h-5 min-w-5 place-items-center rounded-full bg-cherry px-1 text-[11px] font-bold text-white">
                    {cartQty(item.id)}
                  </span>
                )}
              </button>
            </li>
          ))}
        </ul>
      </section>
    </div>
  )
}
