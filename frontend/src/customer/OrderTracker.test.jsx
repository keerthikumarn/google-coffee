import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { act, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { api, ApiError } from '../api.js'
import { FakeEventSource, installFakeEventSource } from '../test/FakeEventSource.js'
import OrderTracker from './OrderTracker.jsx'

const session = { id: 's1', name: 'Asha' }

function view(status = 'PLACED', { eta = 6, position = 1, ...order } = {}) {
  return {
    etaMinutes: eta,
    queuePosition: position,
    order: {
      id: 'o1', code: '123', customerName: 'Asha', status, total: 640,
      items: [{ itemId: 'espresso', name: 'Espresso', qty: 2, unitPrice: 160, note: 'extra hot' }],
      ...order,
    },
  }
}

/** Let resolved promises (mocked api calls) and any timers due right now run. */
const flush = () => act(() => vi.advanceTimersByTimeAsync(0))
const advance = (ms) => act(() => vi.advanceTimersByTimeAsync(ms))

async function renderTracker(initial = view(), props = {}) {
  vi.spyOn(api, 'order').mockResolvedValue(initial)
  const utils = render(<OrderTracker session={session} orderId="o1" onOrderAgain={vi.fn()} onMissing={vi.fn()} {...props} />)
  await flush()
  return utils
}

beforeEach(() => {
  vi.useFakeTimers()
  installFakeEventSource()
})

afterEach(() => {
  vi.useRealTimers()
  vi.unstubAllGlobals()
})

describe('OrderTracker: what the guest sees', () => {
  it('invites the guest to order when there is no active order', () => {
    render(<OrderTracker session={session} orderId={null} />)

    expect(screen.getByText('No active order')).toBeInTheDocument()
    expect(FakeEventSource.instances).toHaveLength(0)
  })

  it('shows the pickup code, items and total once the order loads', async () => {
    await renderTracker()

    expect(screen.getByText('#123')).toBeInTheDocument()
    expect(screen.getByText(/2 × Espresso/)).toHaveTextContent('2 × Espresso, extra hot')
    expect(screen.getByText('₹320')).toBeInTheDocument()   // 2 x ₹160 line
    expect(screen.getByText('₹640')).toBeInTheDocument()   // order total
    expect(api.order).toHaveBeenCalledWith('o1', 's1')
  })

  it.each([
    ['PLACED', 'We’ve got your order'],
    ['PREPARING', 'Your order is being made'],
    ['READY', 'Ready at the counter'],
    ['COLLECTED', 'Enjoy!'],
    ['CANCELLED', 'This order was cancelled'],
  ])('shows the right headline for %s', async (status, headline) => {
    await renderTracker(view(status))

    expect(screen.getByRole('heading', { level: 2 })).toHaveTextContent(headline)
  })

  describe('progress steps', () => {
    const reached = () => within(screen.getByRole('list', { name: 'Order progress' }))
      .getAllByRole('listitem')
      .map((li) => (li.querySelector('span').className.includes('text-leaf') ? 1 : 0))

    it.each([
      ['PLACED', [1, 0, 0]],
      ['PREPARING', [1, 1, 0]],
      ['READY', [1, 1, 1]],
      ['COLLECTED', [1, 1, 1]],
    ])('%s lights up the steps reached so far', async (status, expected) => {
      await renderTracker(view(status))

      expect(reached()).toEqual(expected)
      expect(screen.getAllByRole('listitem').slice(0, 3).map((li) => li.textContent))
        .toEqual(['Received', 'Brewing', 'Ready'])
    })

    it('hides the progress steps for a cancelled order', async () => {
      await renderTracker(view('CANCELLED'))

      expect(screen.queryByRole('list', { name: 'Order progress' })).not.toBeInTheDocument()
    })
  })

  describe('status message', () => {
    it('quotes the wait without a queue note when the guest is next', async () => {
      await renderTracker(view('PLACED', { eta: 4, position: 1 }))

      expect(screen.getByText('About 4 min.')).toBeInTheDocument()
    })

    it('says how many orders are ahead, in the singular and the plural', async () => {
      const { unmount } = await renderTracker(view('PLACED', { eta: 7, position: 2 }))
      expect(screen.getByText('About 7 min, 1 order ahead of you.')).toBeInTheDocument()
      unmount()

      await renderTracker(view('PREPARING', { eta: 9, position: 4 }))
      expect(screen.getByText('About 9 min, 3 orders ahead of you.')).toBeInTheDocument()
    })

    it('tells the guest by name when the order is ready', async () => {
      await renderTracker(view('READY'))

      expect(screen.getByText('Collect from the counter, Asha.')).toBeInTheDocument()
    })

    it.each(['COLLECTED', 'CANCELLED'])('thanks the guest and offers a new order when %s', async (status) => {
      const onOrderAgain = vi.fn()
      await renderTracker(view(status), { onOrderAgain })

      expect(screen.getByText('Thanks for visiting Google Coffee.')).toBeInTheDocument()
      await userEvent.setup({ delay: null }).click(screen.getByRole('button', { name: 'Order something else' }))
      expect(onOrderAgain).toHaveBeenCalledTimes(1)
    })

    it('does not offer a new order while this one is still in progress', async () => {
      await renderTracker(view('PREPARING'))

      expect(screen.queryByRole('button', { name: 'Order something else' })).not.toBeInTheDocument()
    })
  })
})

describe('OrderTracker: staying up to date', () => {
  it('opens the order stream for this guest and shows polling until it delivers', async () => {
    await renderTracker()

    expect(FakeEventSource.last.url).toBe('/api/orders/o1/stream?sessionId=s1')
    expect(screen.getByText('Auto-refreshing every few seconds')).toBeInTheDocument()
  })

  it('applies a pushed update immediately and shows the live indicator', async () => {
    await renderTracker(view('PLACED'))

    act(() => FakeEventSource.last.emit('order', view('READY')))

    expect(screen.getByRole('heading', { level: 2 })).toHaveTextContent('Ready at the counter')
    expect(screen.getByText('Live updates on')).toBeInTheDocument()
  })

  it('falls back to the polling indicator when the stream errors', async () => {
    await renderTracker()
    act(() => FakeEventSource.last.emit('order', view('PREPARING')))

    act(() => FakeEventSource.last.fail())

    expect(screen.getByText('Auto-refreshing every few seconds')).toBeInTheDocument()
  })

  it('polls every three seconds until the stream delivers, then stops polling', async () => {
    await renderTracker()
    expect(api.order).toHaveBeenCalledTimes(1)

    await advance(3000)
    expect(api.order).toHaveBeenCalledTimes(2)

    act(() => FakeEventSource.last.emit('order', view('PREPARING')))
    await advance(9000)
    expect(api.order).toHaveBeenCalledTimes(2)
  })

  it('resumes polling if the stream drops after having worked', async () => {
    await renderTracker()
    act(() => FakeEventSource.last.emit('order', view('PREPARING')))
    act(() => FakeEventSource.last.fail())

    await advance(3000)

    expect(api.order).toHaveBeenCalledTimes(2)
  })

  it('reconnects three seconds after the stream closes, but not after a recoverable error', async () => {
    await renderTracker()

    act(() => FakeEventSource.last.fail())
    await advance(3000)
    expect(FakeEventSource.instances).toHaveLength(1)

    act(() => FakeEventSource.last.fail({ closed: true }))
    await advance(3000)
    expect(FakeEventSource.instances).toHaveLength(2)
  })

  it('forgets an order the server no longer knows, and stops retrying once the parent drops it', async () => {
    const onMissing = vi.fn()
    vi.spyOn(api, 'order').mockRejectedValue(new ApiError('Order not found', 404))
    const { rerender } = render(<OrderTracker session={session} orderId="o1" onMissing={onMissing} />)
    await flush()

    expect(onMissing).toHaveBeenCalledTimes(1)
    expect(FakeEventSource.last.closed).toBe(true)

    // CustomerApp reacts to onMissing by clearing the order id, which tears the tracker's effect down.
    rerender(<OrderTracker session={session} orderId={null} onMissing={onMissing} />)
    const callsSoFar = api.order.mock.calls.length
    await advance(10000)
    expect(api.order).toHaveBeenCalledTimes(callsSoFar)
  })

  it('shows the error when the first load fails for another reason', async () => {
    vi.spyOn(api, 'order').mockRejectedValue(new ApiError('Café server is down', 503))
    render(<OrderTracker session={session} orderId="o1" onMissing={vi.fn()} />)
    await flush()

    expect(screen.getByRole('alert')).toHaveTextContent('Café server is down')
  })

  it('closes the stream and stops polling when the guest leaves', async () => {
    const { unmount } = await renderTracker()
    const stream = FakeEventSource.last
    const calls = api.order.mock.calls.length

    unmount()
    await advance(10000)

    expect(stream.closed).toBe(true)
    expect(api.order).toHaveBeenCalledTimes(calls)
  })
})

describe('OrderTracker: feedback card', () => {
  const user = () => userEvent.setup({ delay: null })
  const sendButton = () => screen.getByRole('button', { name: /send feedback/i })

  it('asks for a rating before sending anything', async () => {
    vi.spyOn(api, 'feedback')
    await renderTracker()

    await user().click(sendButton())

    expect(screen.getByText('Pick a face first.')).toBeInTheDocument()
    expect(api.feedback).not.toHaveBeenCalled()
  })

  it('marks the chosen face and clears the "pick a face" prompt', async () => {
    await renderTracker()
    const u = user()
    await u.click(sendButton())

    await u.click(screen.getByRole('radio', { name: 'Happy' }))

    expect(screen.getByRole('radio', { name: 'Happy' })).toHaveAttribute('aria-checked', 'true')
    expect(screen.getByRole('radio', { name: 'Unhappy' })).toHaveAttribute('aria-checked', 'false')
    expect(screen.queryByText('Pick a face first.')).not.toBeInTheDocument()
  })

  it('sends the rating and trimmed comment for this order, then thanks the guest', async () => {
    vi.spyOn(api, 'feedback').mockResolvedValue({})
    await renderTracker()
    const u = user()

    await u.click(screen.getByRole('radio', { name: 'Loved it' }))
    await u.type(screen.getByLabelText('Comment'), '  great music ')
    await u.click(sendButton())
    await flush()

    expect(api.feedback).toHaveBeenCalledWith('s1', 'o1', 5, 'great music')
    expect(screen.getByText('Thanks, the team sees this right away.')).toBeInTheDocument()
    expect(screen.queryByRole('radiogroup')).not.toBeInTheDocument()
  })

  it('disables the button while sending', async () => {
    vi.spyOn(api, 'feedback').mockReturnValue(new Promise(() => {}))
    await renderTracker()
    const u = user()
    await u.click(screen.getByRole('radio', { name: 'Okay' }))

    await u.click(sendButton())

    expect(sendButton()).toBeDisabled()
  })

  it('shows the error and lets the guest try again when sending fails', async () => {
    vi.spyOn(api, 'feedback').mockRejectedValue(new ApiError('Order not found', 404))
    await renderTracker()
    const u = user()
    await u.click(screen.getByRole('radio', { name: 'Happy' }))

    await u.click(sendButton())
    await flush()

    expect(screen.getByText('Order not found')).toBeInTheDocument()
    expect(sendButton()).toBeEnabled()
    expect(screen.queryByText('Thanks, the team sees this right away.')).not.toBeInTheDocument()
  })
})
