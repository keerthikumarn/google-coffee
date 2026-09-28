import { useCallback, useEffect, useState } from 'react'
import { Coffee, MessageCircle, ShoppingBag, UtensilsCrossed } from 'lucide-react'
import { api, rupees, store } from '../api.js'
import { ErrorNote, Spinner } from '../ui/bits.jsx'
import Welcome from './Welcome.jsx'
import MenuTab from './MenuTab.jsx'
import BrewChat from './BrewChat.jsx'
import CartSheet from './CartSheet.jsx'
import OrderTracker from './OrderTracker.jsx'

const TABS = [
  { id: 'menu', label: 'Menu', Icon: UtensilsCrossed },
  { id: 'brew', label: 'Ask Brew', Icon: MessageCircle },
  { id: 'order', label: 'My order', Icon: Coffee },
]

export default function CustomerApp() {
  const [session, setSession] = useState(() => store.get('gc.session'))
  const [menu, setMenu] = useState([])
  const [menuError, setMenuError] = useState('')
  const [status, setStatus] = useState(null)
  const [tab, setTab] = useState(() => (store.get('gc.orderId') ? 'order' : 'menu'))
  const [cart, setCart] = useState([])
  const [cartOpen, setCartOpen] = useState(false)
  const [placing, setPlacing] = useState(false)
  const [placeError, setPlaceError] = useState('')
  const [orderId, setOrderId] = useState(() => store.get('gc.orderId'))
  const [messages, setMessages] = useState([])
  const [toast, setToast] = useState('')

  const loadMenu = useCallback(() => {
    setMenuError('')
    api.menu().then(setMenu).catch((e) => setMenuError(e.message))
  }, [])

  useEffect(() => { loadMenu() }, [loadMenu])

  // Validate a stored session (it may have been created against another project).
  useEffect(() => {
    if (!session) return
    api.session(session.id).catch((e) => {
      if (e.status === 404) {
        store.remove('gc.session'); store.remove('gc.orderId')
        setSession(null); setOrderId(null); setTab('menu')
      }
    })
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  useEffect(() => {
    const load = () => api.cafeStatus().then(setStatus).catch(() => {})
    load()
    const t = setInterval(load, 30000)
    return () => clearInterval(t)
  }, [])

  useEffect(() => {
    if (!toast) return
    const t = setTimeout(() => setToast(''), 1800)
    return () => clearTimeout(t)
  }, [toast])

  const addToCart = (item) => {
    setCart((c) => {
      const existing = c.find((l) => l.item.id === item.id)
      if (existing) return c.map((l) => (l.item.id === item.id ? { ...l, qty: Math.min(10, l.qty + 1) } : l))
      return [...c, { item, qty: 1, note: '' }]
    })
    setToast(`Added ${item.name}`)
  }
  const setQty = (id, qty) => setCart((c) => (qty <= 0 ? c.filter((l) => l.item.id !== id) : c.map((l) => (l.item.id === id ? { ...l, qty } : l))))
  const setNote = (id, note) => setCart((c) => c.map((l) => (l.item.id === id ? { ...l, note } : l)))
  const cartQty = (id) => cart.find((l) => l.item.id === id)?.qty || 0
  const cartCount = cart.reduce((s, l) => s + l.qty, 0)
  const cartTotal = cart.reduce((s, l) => s + l.qty * l.item.price, 0)

  async function placeOrder() {
    setPlacing(true)
    setPlaceError('')
    try {
      const view = await api.placeOrder(session.id, cart.map((l) => ({ itemId: l.item.id, qty: l.qty, note: l.note })))
      store.set('gc.orderId', view.order.id)
      setOrderId(view.order.id)
      setCart([])
      setCartOpen(false)
      setTab('order')
    } catch (e) {
      setPlaceError(e.message)
    } finally {
      setPlacing(false)
    }
  }

  function startSession(s) {
    store.set('gc.session', s)
    store.remove('gc.orderId') // an order from an earlier session never carries over
    setOrderId(null)
    setSession(s)
  }

  const forgetOrder = useCallback(() => {
    store.remove('gc.orderId')
    setOrderId(null)
  }, [])

  function signOut() {
    store.remove('gc.session'); store.remove('gc.orderId')
    setSession(null); setOrderId(null); setCart([]); setMessages([]); setTab('menu')
  }

  if (!session) return <Welcome onStart={startSession} />

  return (
    <div className="mx-auto min-h-screen max-w-md px-5 pb-32">
      <header className="sticky top-0 z-20 -mx-5 flex items-center justify-between bg-steam/90 px-5 pb-3 pt-5 backdrop-blur">
        <div>
          <p className="font-display text-xl font-extrabold leading-none tracking-tight">Google Coffee</p>
          <button onClick={signOut} className="mt-1 text-xs text-muted underline-offset-2 hover:underline">
            {session.name}, table {session.table}. Not you?
          </button>
        </div>
        {status && (
          <div className="rounded-full bg-white px-3 py-1.5 text-right text-xs">
            <span className="font-semibold">{status.busyLevel}</span>
            <span className="text-muted"> now, ~{status.waitMinutesForCoffee} min</span>
          </div>
        )}
      </header>

      <main className="pt-2">
        {menuError && <ErrorNote message={menuError} onRetry={loadMenu} />}
        {!menuError && menu.length === 0 && <div className="flex justify-center py-20 text-muted"><Spinner /></div>}
        {menu.length > 0 && tab === 'menu' && <MenuTab session={session} menu={menu} onAdd={addToCart} cartQty={cartQty} />}
        {tab === 'brew' && <BrewChat session={session} messages={messages} setMessages={setMessages} onAdd={addToCart} raised={cartCount > 0} />}
        {tab === 'order' && <OrderTracker session={session} orderId={orderId} onOrderAgain={() => setTab('menu')} onMissing={forgetOrder} />}
      </main>

      {toast && (
        <div role="status" className="anim-rise fixed left-1/2 top-20 z-50 -translate-x-1/2 rounded-full bg-roast px-4 py-2 text-sm font-medium text-steam">
          {toast}
        </div>
      )}

      <div className="fixed inset-x-0 bottom-0 z-30 mx-auto max-w-md px-4 pb-4">
        {cartCount > 0 && (
          <button onClick={() => setCartOpen(true)}
            className="anim-rise mb-2 flex w-full items-center justify-between rounded-2xl bg-leaf px-5 py-3.5 font-semibold text-steam">
            <span className="flex items-center gap-2"><ShoppingBag size={18} aria-hidden="true" /> View order ({cartCount})</span>
            <span>{rupees(cartTotal)}</span>
          </button>
        )}
        <nav aria-label="Sections" className="grid grid-cols-3 rounded-2xl bg-roast p-1.5">
          {TABS.map(({ id, label, Icon }) => (
            <button key={id} onClick={() => setTab(id)} aria-current={tab === id ? 'page' : undefined}
              className={`flex flex-col items-center gap-0.5 rounded-xl py-2 text-xs font-medium transition-colors ${tab === id ? 'bg-steam text-roast' : 'text-steam/75 hover:text-steam'}`}>
              <Icon size={19} aria-hidden="true" />
              {label}
            </button>
          ))}
        </nav>
      </div>

      {cartOpen && (
        <CartSheet cart={cart} setQty={setQty} setNote={setNote} onClose={() => setCartOpen(false)}
          onPlace={placeOrder} placing={placing} error={placeError} />
      )}
    </div>
  )
}
