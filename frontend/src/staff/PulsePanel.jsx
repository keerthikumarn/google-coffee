import { useCallback, useEffect, useState } from 'react'
import { RefreshCw, Sparkles } from 'lucide-react'
import { api } from '../api.js'
import { ErrorNote, Spinner } from '../ui/bits.jsx'

const SENTIMENT_CLS = {
  positive: 'bg-leaf-soft text-leaf',
  neutral: 'bg-crema-soft text-roast',
  negative: 'bg-cherry-soft text-cherry',
}

export default function PulsePanel({ token, onUnauthorized }) {
  const [pulse, setPulse] = useState(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')

  const load = useCallback(async (refresh) => {
    setLoading(true)
    setError('')
    try {
      setPulse(await api.pulse(token, refresh))
    } catch (e) {
      if (e.status === 401) return onUnauthorized()
      setError(e.message)
    } finally {
      setLoading(false)
    }
  }, [token, onUnauthorized])

  useEffect(() => {
    load(false)
    const t = setInterval(() => load(false), 60000)
    return () => clearInterval(t)
  }, [load])

  const score = pulse?.sentimentScore ?? 0
  const barColor = score >= 70 ? 'bg-leaf' : score >= 45 ? 'bg-crema' : 'bg-cherry'

  return (
    <aside aria-label="Room pulse" className="h-fit rounded-3xl bg-roast p-6 text-steam xl:sticky xl:top-6">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2">
          <Sparkles size={18} className="text-crema" aria-hidden="true" />
          <h2 className="font-display text-xl font-bold">Room pulse</h2>
        </div>
        <button onClick={() => load(true)} disabled={loading} aria-label="Refresh room pulse"
          className="grid h-9 w-9 place-items-center rounded-full bg-steam/10 hover:bg-steam/20 disabled:opacity-50">
          {loading ? <Spinner /> : <RefreshCw size={16} />}
        </button>
      </div>
      <p className="mt-1 text-sm text-steam/60">{pulse?.aiProvider || 'AI'} reads the last hour of guest feedback.</p>

      {error && <div className="mt-4"><ErrorNote message={error} /></div>}
      {!pulse && !error && <div className="flex justify-center py-10"><Spinner /></div>}

      {pulse && (
        <div className="mt-5 space-y-5">
          <div>
            <div className="flex items-baseline justify-between">
              <p className="font-display text-3xl font-extrabold">{pulse.mood}</p>
              {pulse.feedbackCount > 0 && (
                <p className="text-sm text-steam/70">{pulse.averageRating}★ from {pulse.feedbackCount}</p>
              )}
            </div>
            {pulse.feedbackCount > 0 && (
              <div className="mt-3 h-2 rounded-full bg-steam/15" role="meter" aria-valuemin={0} aria-valuemax={100} aria-valuenow={score} aria-label="Sentiment">
                <div className={`h-2 rounded-full ${barColor}`} style={{ width: `${Math.max(4, score)}%`, transition: 'width .8s ease' }} />
              </div>
            )}
            <p className="mt-3 leading-relaxed text-steam/85">{pulse.summary}</p>
          </div>

          {pulse.themes.length > 0 && (
            <div className="flex flex-wrap gap-2">
              {pulse.themes.map((t) => (
                <span key={t.label} className={`rounded-full px-3 py-1 text-sm font-medium ${SENTIMENT_CLS[t.sentiment]}`}>
                  {t.label} <span className="opacity-70">{t.mentions}</span>
                </span>
              ))}
            </div>
          )}

          {pulse.actions.length > 0 && (
            <div className="rounded-2xl bg-steam p-4 text-roast">
              <h3 className="text-sm font-semibold">Suggested right now</h3>
              <ul className="mt-2 space-y-2 text-sm">
                {pulse.actions.map((a) => (
                  <li key={a} className="flex gap-2"><span className="mt-1.5 h-1.5 w-1.5 shrink-0 rounded-full bg-leaf" />{a}</li>
                ))}
              </ul>
            </div>
          )}

          {pulse.latest.length > 0 && (
            <div>
              <h3 className="text-sm font-semibold text-steam/70">Latest from guests</h3>
              <ul className="mt-2 space-y-2">
                {pulse.latest.map((f) => (
                  <li key={f.id} className="rounded-xl bg-steam/10 px-3 py-2 text-sm">
                    <span className="font-semibold text-crema">{f.rating}★</span>
                    <span className="text-steam/60"> table {f.table}</span>
                    {f.comment && <p className="mt-0.5 text-steam/90">{f.comment}</p>}
                  </li>
                ))}
              </ul>
            </div>
          )}
          {!pulse.aiAvailable && pulse.feedbackCount > 0 && (
            <p className="text-xs text-steam/50">AI summary unavailable; showing ratings only.</p>
          )}
        </div>
      )}
    </aside>
  )
}
