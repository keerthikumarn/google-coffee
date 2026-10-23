import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { api, ApiError, store } from '../api.js'
import { FakeEventSource, installFakeEventSource } from '../test/FakeEventSource.js'
import CustomerApp from './CustomerApp.jsx'

const espresso = {
  id: 'espresso',
  name: 'Espresso',
  category: 'Espresso bar',
  description: 'Double shot',
  price: 160,
  tags: [],
}
const toast = { id: 'toast', name: 'Toast', category: 'Bakery & bites', description: 'Buttered', price: 320, tags: [] }
const MENU = [espresso, toast]
const SESSION = { id: 's1', name: 'Asha', table: '7', preferences: [] }

const user = () => userEvent.setup({ delay: null })
const add = (name) => user().click(screen.getByRole('button', { name: `Add ${name}` }))
const cartBar = () => screen.queryByRole('button', { name: /view order/i })
const openCart = () => user().click(cartBar())
const sheet = () => within(screen.getByRole('dialog', { name: 'Your order' }))

/**
 * Render the guest app with a working fake backend. By default the guest is a returning one with a stored
 * session; the helper waits until the menu is on screen unless the scenario shows something else first.
 */
async function renderApp({ session = SESSION, orderId = null, menu = MENU, sessionError, statusError } = {}) {
  if (session) store.set('gc.session', session)
  if (orderId) store.set('gc.orderId', orderId)
  vi.spyOn(api, 'menu').mockResolvedValue(menu)
  vi.spyOn(api, 'cafeStatus')
  if (statusError) api.cafeStatus.mockRejectedValue(statusError)
  else api.cafeStatus.mockResolvedValue({ busyLevel: 'Steady', waitMinutesForCoffee: 7 })
  vi.spyOn(api, 'session')
  if (sessionError) api.session.mockRejectedValue(sessionError)
  else api.session.mockResolvedValue(session)
  vi.spyOn(api, 'recommendations').mockResolvedValue({ headline: 'Picked for you', picks: [] })
  vi.spyOn(api, 'estimate').mockResolvedValue({ minutes: 5 })
  vi.spyOn(api, 'order').mockResolvedValue({
    etaMinutes: 5,
    queuePosition: 1,
    order: { id: 'o1', code: '123', customerName: 'Asha', status: 'PLACED', total: 160, items: [] },
  })
  const utils = render(<CustomerApp />)
  if (session && !orderId && !sessionError) await screen.findByRole('button', { name: 'Add Espresso' })
  return utils
}

beforeEach(() => {
  localStorage.clear()
  installFakeEventSource()
})

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('CustomerApp: getting in', () => {
  it('shows the welcome form to a guest with no session', async () => {
    await renderApp({ session: null })

    expect(screen.getByLabelText('Your name')).toBeInTheDocument()
  })

  it('opens straight to the menu for a returning guest, with their name and table', async () => {
    await renderApp()

    expect(screen.getByText(/Asha, table 7/)).toBeInTheDocument()
    expect(api.session).toHaveBeenCalledWith('s1')
  })

  it('sends a guest back to the welcome form when their stored session is unknown to the server', async () => {
    store.set('gc.orderId', 'old-order')
    await renderApp({ sessionError: new ApiError('Session not found', 404) })

    expect(await screen.findByLabelText('Your name')).toBeInTheDocument()
    expect(store.get('gc.session')).toBeNull()
    expect(store.get('gc.orderId')).toBeNull()
  })

  it('keeps a stored session when the server merely fails to answer', async () => {
    await renderApp({ sessionError: new ApiError('Café server is down', 503) })

    expect(await screen.findByRole('button', { name: 'Add Espresso' })).toBeInTheDocument()
    expect(store.get('gc.session')).toEqual(SESSION)
  })

  it('signing out forgets the guest and empties the cart', async () => {
    await renderApp()
    await add('Espresso')

    await user().click(screen.getByRole('button', { name: /not you\?/i }))

    expect(screen.getByLabelText('Your name')).toBeInTheDocument()
    expect(store.get('gc.session')).toBeNull()
  })

  it('opens on the order tab when the guest has an order in progress', async () => {
    store.set('gc.orderId', 'o1')
    await renderApp({ orderId: 'o1' })

    expect(await screen.findByText('#123')).toBeInTheDocument()
    expect(api.order).toHaveBeenCalledWith('o1', 's1')
  })
})

describe('CustomerApp: the menu and the bar status', () => {
  it('shows how busy the bar is', async () => {
    await renderApp()

    expect(await screen.findByText('Steady')).toBeInTheDocument()
    expect(screen.getByText(/~7 min/)).toBeInTheDocument()
  })

  it('carries on quietly when the bar status cannot be loaded', async () => {
    await renderApp({ statusError: new Error('down') })

    expect(screen.getByRole('button', { name: 'Add Espresso' })).toBeInTheDocument()
    expect(screen.queryByText(/now, ~/)).not.toBeInTheDocument()
  })

  it('shows the menu error with a retry that loads the menu', async () => {
    store.set('gc.session', SESSION)
    vi.spyOn(api, 'cafeStatus').mockResolvedValue(null)
    vi.spyOn(api, 'session').mockResolvedValue(SESSION)
    vi.spyOn(api, 'recommendations').mockResolvedValue({ headline: 'Picked', picks: [] })
    vi.spyOn(api, 'menu').mockRejectedValueOnce(new Error('Café server is down')).mockResolvedValue(MENU)
    render(<CustomerApp />)

    expect(await screen.findByRole('alert')).toHaveTextContent('Café server is down')
    await user().click(screen.getByRole('button', { name: 'Try again' }))

    expect(await screen.findByRole('button', { name: 'Add Espresso' })).toBeInTheDocument()
  })
})

describe('CustomerApp: adding to the cart (addToCart)', () => {
  it('shows the cart bar with the count and total after the first item', async () => {
    await renderApp()
    expect(cartBar()).not.toBeInTheDocument()

    await add('Espresso')

    expect(cartBar()).toHaveTextContent('View order (1)')
    expect(cartBar()).toHaveTextContent('₹160')
  })

  it('confirms with a short message that goes away by itself', async () => {
    await renderApp()

    await add('Toast')

    expect(screen.getByRole('status')).toHaveTextContent('Added Toast')
    await vi.waitFor(() => expect(screen.queryByRole('status')).not.toBeInTheDocument(), { timeout: 3000 })
  })

  it('adding the same item again raises its quantity instead of adding a second line', async () => {
    await renderApp()

    await add('Espresso')
    await add('Espresso')

    expect(cartBar()).toHaveTextContent('View order (2)')
    expect(cartBar()).toHaveTextContent('₹320')
    await openCart()
    expect(sheet().getAllByRole('listitem')).toHaveLength(1)
  })

  it('shows the quantity in the cart on the menu item', async () => {
    await renderApp()

    await add('Espresso')
    await add('Espresso')

    expect(screen.getByRole('button', { name: 'Add Espresso' })).toHaveTextContent('2')
    expect(screen.getByRole('button', { name: 'Add Toast' })).not.toHaveTextContent(/\d/)
  })

  it('never lets one item go above ten', async () => {
    await renderApp()

    for (let i = 0; i < 12; i++) await add('Espresso')

    expect(cartBar()).toHaveTextContent('View order (10)')
    expect(cartBar()).toHaveTextContent('₹1,600')
  })

  it('keeps different items on separate lines and totals them', async () => {
    await renderApp()

    await add('Espresso')
    await add('Toast')

    expect(cartBar()).toHaveTextContent('View order (2)')
    expect(cartBar()).toHaveTextContent('₹480')
    await openCart()
    expect(sheet().getAllByRole('listitem')).toHaveLength(2)
  })

  it('keeps the cart when the guest moves between tabs', async () => {
    await renderApp()
    await add('Espresso')

    await user().click(screen.getByRole('button', { name: 'Ask Brew' }))
    await user().click(screen.getByRole('button', { name: 'Menu' }))

    expect(cartBar()).toHaveTextContent('View order (1)')
  })
})

describe('CustomerApp: changing quantities in the cart (setQty)', () => {
  it('raises and lowers a quantity and keeps the total in step', async () => {
    await renderApp()
    await add('Espresso')
    await openCart()
    const u = user()

    await u.click(sheet().getByRole('button', { name: 'Add one Espresso' }))
    await u.click(sheet().getByRole('button', { name: 'Add one Espresso' }))
    expect(cartBar()).toHaveTextContent('View order (3)')
    expect(sheet().getByText('₹480', { selector: 'span.font-display' })).toBeInTheDocument()

    await u.click(sheet().getByRole('button', { name: 'Remove one Espresso' }))
    expect(cartBar()).toHaveTextContent('View order (2)')
  })

  it('removes a line when its quantity reaches zero, leaving the others', async () => {
    await renderApp()
    await add('Espresso')
    await add('Toast')
    await openCart()

    await user().click(sheet().getByRole('button', { name: 'Remove one Espresso' }))

    expect(sheet().queryByText('Espresso')).not.toBeInTheDocument()
    expect(sheet().getByText('Toast')).toBeInTheDocument()
    expect(cartBar()).toHaveTextContent('View order (1)')
    expect(cartBar()).toHaveTextContent('₹320')
  })

  it('shows the empty cart and drops the cart bar once the last line is gone', async () => {
    await renderApp()
    await add('Espresso')
    await openCart()

    await user().click(sheet().getByRole('button', { name: 'Remove one Espresso' }))

    expect(sheet().getByText(/nothing here yet/i)).toBeInTheDocument()
    expect(cartBar()).not.toBeInTheDocument()
  })

  it('keeps a barista note when the quantity changes, and notes do not change the total', async () => {
    await renderApp()
    await add('Espresso')
    await openCart()
    const u = user()

    await u.type(sheet().getByLabelText('Note for Espresso'), 'extra hot')
    await u.click(sheet().getByRole('button', { name: 'Add one Espresso' }))

    expect(sheet().getByLabelText('Note for Espresso')).toHaveValue('extra hot')
    expect(cartBar()).toHaveTextContent('₹320')
  })
})

describe('CustomerApp: placing the order', () => {
  async function fillCartAndOpen() {
    await renderApp()
    await add('Espresso')
    await add('Espresso')
    await add('Toast')
    await openCart()
    await user().type(sheet().getByLabelText('Note for Toast'), 'warm please')
  }

  it('sends the lines with their notes, then moves the guest to the order tracker with an empty cart', async () => {
    vi.spyOn(api, 'placeOrder').mockResolvedValue({ order: { id: 'o1' } })
    await fillCartAndOpen()

    await user().click(sheet().getByRole('button', { name: /place order/i }))

    expect(api.placeOrder).toHaveBeenCalledWith('s1', [
      { itemId: 'espresso', qty: 2, note: '' },
      { itemId: 'toast', qty: 1, note: 'warm please' },
    ])
    expect(await screen.findByText('#123')).toBeInTheDocument()
    expect(store.get('gc.orderId')).toBe('o1')
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(cartBar()).not.toBeInTheDocument()
    expect(FakeEventSource.last.url).toContain('/api/orders/o1/stream')
  })

  it('shows the server error and keeps the cart so the guest can try again', async () => {
    vi.spyOn(api, 'placeOrder').mockRejectedValue(new ApiError('Unknown menu item: toast', 400))
    await fillCartAndOpen()

    await user().click(sheet().getByRole('button', { name: /place order/i }))

    expect(await sheet().findByText('Unknown menu item: toast')).toBeInTheDocument()
    expect(cartBar()).toHaveTextContent('View order (3)')
    expect(sheet().getByRole('button', { name: /place order/i })).toBeEnabled()
  })

  it('blocks a second submission while the first is still in flight', async () => {
    vi.spyOn(api, 'placeOrder').mockReturnValue(new Promise(() => {}))
    await fillCartAndOpen()

    await user().click(sheet().getByRole('button', { name: /place order/i }))

    expect(sheet().getByRole('button', { name: /place order/i })).toBeDisabled()
    expect(api.placeOrder).toHaveBeenCalledTimes(1)
  })
})
