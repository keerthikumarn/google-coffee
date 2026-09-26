import { useState } from 'react'
import { api } from '../api.js'
import { ErrorNote, PREFERENCES, Spinner } from '../ui/bits.jsx'

export default function Welcome({ onStart }) {
  const initialTable = new URLSearchParams(window.location.search).get('table') || ''
  const [name, setName] = useState('')
  const [table, setTable] = useState(initialTable)
  const [prefs, setPrefs] = useState([])
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  const toggle = (id) => setPrefs((p) => (p.includes(id) ? p.filter((x) => x !== id) : [...p, id]))

  async function submit(e) {
    e.preventDefault()
    if (!name.trim()) return setError('Add your name so the barista can call you.')
    if (!table.trim()) return setError('Add your table number. It’s on the QR stand.')
    setBusy(true)
    setError('')
    try {
      onStart(await api.createSession(name.trim(), table.trim(), prefs))
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <main className="mx-auto flex min-h-screen max-w-md flex-col px-6 pb-10 pt-12">
      <div className="anim-rise">
        <p className="text-sm font-medium text-muted">Specialty coffee, Bengaluru</p>
        <h1 className="mt-2 font-display text-[3.4rem] font-extrabold leading-[0.92] tracking-tight text-roast">
          Google<br />Coffee
        </h1>
        <p className="mt-4 max-w-[30ch] text-[17px] leading-relaxed text-roast-soft">
          Order from your table, ask our AI barista what to try, and watch your cup fill up as we make it.
        </p>
      </div>

      <form onSubmit={submit} className="mt-10 space-y-6">
        <div className="grid grid-cols-[1fr_96px] gap-3">
          <label className="block">
            <span className="text-sm font-semibold">Your name</span>
            <input value={name} onChange={(e) => setName(e.target.value)} maxLength={40} autoComplete="given-name"
              className="mt-1.5 w-full rounded-xl border border-line bg-white px-4 py-3 text-base outline-none focus:border-leaf" placeholder="Priya" />
          </label>
          <label className="block">
            <span className="text-sm font-semibold">Table</span>
            <input value={table} onChange={(e) => setTable(e.target.value)} maxLength={10} inputMode="numeric"
              className="mt-1.5 w-full rounded-xl border border-line bg-white px-4 py-3 text-base outline-none focus:border-leaf" placeholder="7" />
          </label>
        </div>

        <fieldset>
          <legend className="text-sm font-semibold">Anything we should know?</legend>
          <p className="text-sm text-muted">Brew will never suggest something that breaks these.</p>
          <div className="mt-3 flex flex-wrap gap-2">
            {PREFERENCES.map((p) => {
              const on = prefs.includes(p.id)
              return (
                <button type="button" key={p.id} onClick={() => toggle(p.id)} aria-pressed={on}
                  className={`rounded-full border px-4 py-2 text-sm font-medium transition-colors ${on ? 'border-leaf bg-leaf text-steam' : 'border-line bg-white text-roast hover:border-roast-soft'}`}>
                  {p.label}
                </button>
              )
            })}
          </div>
        </fieldset>

        <ErrorNote message={error} />

        <button disabled={busy} className="flex w-full items-center justify-center gap-2 rounded-2xl bg-roast py-4 text-base font-semibold text-steam transition hover:bg-roast-soft disabled:opacity-60">
          {busy && <Spinner />} Start ordering
        </button>
        <p className="text-center text-xs text-muted">Pay at the counter when you collect. Menu shown is sample data.</p>
      </form>
    </main>
  )
}
