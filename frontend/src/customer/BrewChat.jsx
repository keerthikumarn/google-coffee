import { useEffect, useRef, useState } from 'react'
import { Plus, Send } from 'lucide-react'
import { api, rupees } from '../api.js'
import { ItemGlyph, Spinner } from '../ui/bits.jsx'

const STARTERS = [
  'Something cold and not too sweet',
  'What goes well with a croissant?',
  'I need a strong coffee to focus',
  'Surprise me with something new',
]

export default function BrewChat({ session, messages, setMessages, onAdd, raised }) {
  const [input, setInput] = useState('')
  const [busy, setBusy] = useState(false)
  const endRef = useRef(null)

  useEffect(() => { endRef.current?.scrollIntoView({ behavior: 'smooth', block: 'end' }) }, [messages, busy])

  async function send(text) {
    const message = text.trim()
    if (!message || busy) return
    const history = messages.map((m) => ({ role: m.role, text: m.text })).slice(-10)
    setMessages((m) => [...m, { role: 'user', text: message }])
    setInput('')
    setBusy(true)
    try {
      const res = await api.chat(session.id, message, history)
      setMessages((m) => [...m, { role: 'assistant', text: res.reply, suggestions: res.suggestions }])
    } catch (err) {
      setMessages((m) => [...m, { role: 'assistant', text: err.message, error: true, suggestions: [] }])
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="flex min-h-[calc(100vh-190px)] flex-col">
      <div className="flex-1 space-y-4 pb-4">
        <div className="rounded-3xl bg-white p-5">
          <p className="font-display text-xl font-bold">Hi {session.name}, I’m Brew.</p>
          <p className="mt-1 text-[15px] leading-relaxed text-roast-soft">
            Tell me what you’re in the mood for. I only suggest what’s actually on our menu
            {session.preferences?.length ? ', and I’ll respect your preferences.' : '.'}
          </p>
          {messages.length === 0 && (
            <div className="mt-4 flex flex-wrap gap-2">
              {STARTERS.map((s) => (
                <button key={s} onClick={() => send(s)} className="rounded-full bg-steam px-3.5 py-2 text-left text-sm hover:bg-leaf-soft">
                  {s}
                </button>
              ))}
            </div>
          )}
        </div>

        {messages.map((m, i) => (
          <div key={i} className={`anim-rise flex ${m.role === 'user' ? 'justify-end' : 'justify-start'}`}>
            <div className={`max-w-[85%] ${m.role === 'user' ? 'rounded-3xl rounded-br-md bg-leaf px-4 py-3 text-steam' : ''}`}>
              {m.role === 'user' ? m.text : (
                <div className="space-y-2">
                  <p className={`rounded-3xl rounded-bl-md px-4 py-3 ${m.error ? 'bg-cherry-soft text-cherry' : 'bg-white'}`}>{m.text}</p>
                  {m.suggestions?.map(({ item, reason }) => (
                    <div key={item.id} className="flex items-center gap-3 rounded-2xl border border-line bg-white p-3">
                      <ItemGlyph category={item.category} />
                      <div className="min-w-0 flex-1">
                        <p className="font-semibold leading-tight">{item.name}</p>
                        <p className="text-sm leading-snug text-muted">{reason || item.description}</p>
                      </div>
                      <button onClick={() => onAdd(item)} className="flex shrink-0 items-center gap-1 rounded-full bg-roast px-3 py-2 text-sm font-semibold text-steam hover:bg-roast-soft">
                        <Plus size={15} aria-hidden="true" /> {rupees(item.price)}
                      </button>
                    </div>
                  ))}
                </div>
              )}
            </div>
          </div>
        ))}
        {busy && (
          <div className="flex items-center gap-2 text-sm text-muted"><Spinner /> Brew is thinking</div>
        )}
        <div ref={endRef} />
      </div>

      <form onSubmit={(e) => { e.preventDefault(); send(input) }}
        className={`sticky ${raised ? "bottom-[148px]" : "bottom-[92px]"} flex items-center gap-2 rounded-full border border-line bg-white p-1.5 pl-5 shadow-[0_8px_24px_-12px_rgba(42,26,18,.35)]`}>
        <label htmlFor="brew-input" className="sr-only">Message Brew</label>
        <input id="brew-input" value={input} onChange={(e) => setInput(e.target.value)} maxLength={500}
          placeholder="Ask for a recommendation" className="min-w-0 flex-1 bg-transparent py-2 outline-none" />
        <button disabled={busy || !input.trim()} aria-label="Send"
          className="grid h-10 w-10 place-items-center rounded-full bg-leaf text-steam disabled:opacity-40">
          <Send size={17} />
        </button>
      </form>
    </div>
  )
}
