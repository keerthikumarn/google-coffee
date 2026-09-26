import { useState } from 'react'
import { api, store } from '../api.js'
import { ErrorNote, Spinner } from '../ui/bits.jsx'
import Dashboard from './Dashboard.jsx'

export default function StaffApp() {
  const [token, setToken] = useState(() => store.get('gc.staffToken'))

  if (!token) return <Login onToken={(t) => { store.set('gc.staffToken', t); setToken(t) }} />
  return <Dashboard token={token} onSignOut={() => { store.remove('gc.staffToken'); setToken(null) }} />
}

function Login({ onToken }) {
  const [pin, setPin] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  async function submit(e) {
    e.preventDefault()
    if (!pin) return setError('Enter the café PIN.')
    setBusy(true)
    setError('')
    try {
      const { token } = await api.staffLogin(pin)
      onToken(token)
    } catch (err) {
      setError(err.message)
      setPin('')
    } finally {
      setBusy(false)
    }
  }

  return (
    <main className="grid min-h-screen place-items-center bg-roast px-6">
      <form onSubmit={submit} className="w-full max-w-sm rounded-3xl bg-steam p-8">
        <p className="font-display text-3xl font-extrabold tracking-tight">Google Coffee</p>
        <p className="mt-1 text-muted">Bar and floor dashboard</p>
        <label className="mt-8 block">
          <span className="text-sm font-semibold">Staff PIN</span>
          <input type="password" inputMode="numeric" autoComplete="current-password" value={pin} onChange={(e) => setPin(e.target.value)}
            className="mt-1.5 w-full rounded-xl border border-line bg-white px-4 py-3 text-center text-2xl tracking-[0.5em] outline-none focus:border-leaf" />
        </label>
        <div className="mt-4"><ErrorNote message={error} /></div>
        <button disabled={busy} className="mt-4 flex w-full items-center justify-center gap-2 rounded-2xl bg-leaf py-3.5 font-semibold text-steam disabled:opacity-60">
          {busy && <Spinner />} Open dashboard
        </button>
      </form>
    </main>
  )
}
