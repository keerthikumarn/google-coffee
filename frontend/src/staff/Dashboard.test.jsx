import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { act, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { api, ApiError } from '../api.js'
import { FakeEventSource, installFakeEventSource } from '../test/FakeEventSource.js'
import Dashboard from './Dashboard.jsx'

// The room-pulse panel has its own polling and tests; keep it out of the way here.
vi.mock('./PulsePanel.jsx', () => ({ default: () => <aside aria-label="Room pulse" /> }))

const TOKEN = 'tok en'

function entry(id, code, status, { name = 'Asha', table = '7', ageMin = 1, prep = 4, eta = 5, note = '' } = {}) {
  return {
    etaMinutes: eta,
    order: {
      id,
      code,
      customerName: name,
      table,
      status,
      prepMinutes: prep,
      total: 320,
      createdAt: Date.now() - ageMin * 60000,
      items: [{ itemId: 'espresso', name: 'Espresso', qty: 2, unitPrice: 160, note }],
    },
  }
}

const board = (orders, activeBaristas = 2) => ({ activeBaristas, orders })

const flush = () => act(() => vi.advanceTimersByTimeAsync(0))
const advance = (ms) => act(() => vi.advanceTimersByTimeAsync(ms))
const push = (data) => act(() => FakeEventSource.last.emit('board', data))
const column = (title) => screen.getByRole('region', { name: title })
const card = (code) => screen.getByText(`#${code}`).closest('article')
const button = (name) => screen.getByRole('button', { name })

async function renderDashboard(initial = board([entry('o1', '101', 'PLACED')]), props = {}) {
  const onSignOut = props.onSignOut ?? vi.fn()
  const utils = render(<Dashboard token={TOKEN} onSignOut={onSignOut} />)
  if (initial) await push(initial)
  return { ...utils, onSignOut }
}

beforeEach(() => {
  vi.useFakeTimers()
  installFakeEventSource()
  vi.spyOn(api, 'setStatus').mockResolvedValue({})
  vi.spyOn(api, 'setBaristas').mockResolvedValue({})
  vi.spyOn(api, 'staffBoard').mockResolvedValue(board([]))
})

afterEach(() => {
  vi.useRealTimers()
  vi.unstubAllGlobals()
})

const user = () => userEvent.setup({ delay: null })

describe('Dashboard: the order board', () => {
  it('shows a loading board until the first update arrives', async () => {
    await renderDashboard(null)

    expect(screen.getByText('–')).toBeInTheDocument()
    expect(screen.queryByText('Nothing here right now.')).not.toBeInTheDocument()
  })

  it('opens the staff stream with the encoded token', async () => {
    await renderDashboard(null)

    expect(FakeEventSource.last.url).toBe('/api/staff/stream?token=tok%20en')
  })

  it('puts each order in the column for its status with counts', async () => {
    await renderDashboard(
      board([entry('o1', '101', 'PLACED'), entry('o2', '102', 'PLACED'), entry('o3', '103', 'PREPARING')]),
    )

    expect(within(column('New')).getAllByRole('article')).toHaveLength(2)
    expect(within(column('Brewing')).getByText('#103')).toBeInTheDocument()
    expect(within(column('New')).getByText('2')).toBeInTheDocument()
    expect(within(column('Ready for pickup')).getByText('Nothing here right now.')).toBeInTheDocument()
  })

  it('shows the guest, table, items, note, total and ETA on a card', async () => {
    await renderDashboard(
      board([entry('o1', '101', 'PLACED', { name: 'Ravi', table: '2', eta: 9, note: 'extra hot' })]),
    )

    const c = within(card('101'))
    expect(c.getByText('Ravi, table 2')).toBeInTheDocument()
    expect(c.getByText('Espresso', { exact: false })).toBeInTheDocument()
    expect(c.getByText('extra hot')).toBeInTheDocument()
    expect(c.getByText('₹320')).toBeInTheDocument()
    expect(c.getByText('ETA 9 min')).toBeInTheDocument()
  })

  it('shows no ETA for an order that is already ready', async () => {
    await renderDashboard(board([entry('o1', '101', 'READY')]))

    expect(within(card('101')).queryByText(/ETA/)).not.toBeInTheDocument()
  })

  it('shows how long ago an order came in', async () => {
    await renderDashboard(
      board([entry('o1', '101', 'PLACED', { ageMin: 0 }), entry('o2', '102', 'PLACED', { ageMin: 3 })]),
    )

    expect(within(card('101')).getByText('just now')).toBeInTheDocument()
    expect(within(card('102')).getByText('3 min ago')).toBeInTheDocument()
  })

  it('flags an order as late once it is more than five minutes past its prep time', async () => {
    await renderDashboard(
      board([
        entry('late', '101', 'PREPARING', { ageMin: 20, prep: 4 }),
        entry('ok', '102', 'PREPARING', { ageMin: 9, prep: 4 }),
        entry('old-ready', '103', 'READY', { ageMin: 60, prep: 4 }),
      ]),
    )

    expect(within(card('101')).getByText('20 min ago')).toHaveClass('text-cherry')
    expect(within(card('102')).getByText('9 min ago')).not.toHaveClass('text-cherry')
    expect(within(card('103')).getByText('60 min ago')).not.toHaveClass('text-cherry')
  })

  it('shows the barista count from the board', async () => {
    await renderDashboard(board([], 3))

    expect(screen.getByText('3')).toBeInTheDocument()
  })
})

describe('Dashboard: moving an order along (advance)', () => {
  it.each([
    ['PLACED', 'Start', 'PREPARING'],
    ['PREPARING', 'Mark ready', 'READY'],
    ['READY', 'Collected', 'COLLECTED'],
  ])('%s: "%s" sets the order to %s', async (status, label, next) => {
    await renderDashboard(board([entry('o1', '101', status)]))

    await user().click(button(label))

    expect(api.setStatus).toHaveBeenCalledWith(TOKEN, 'o1', next)
  })

  it.each(['PLACED', 'PREPARING'])('lets staff cancel a %s order', async (status) => {
    await renderDashboard(board([entry('o1', '101', status)]))

    await user().click(button('Cancel'))

    expect(api.setStatus).toHaveBeenCalledWith(TOKEN, 'o1', 'CANCELLED')
  })

  it('offers no cancel once an order is ready', async () => {
    await renderDashboard(board([entry('o1', '101', 'READY')]))

    expect(screen.queryByRole('button', { name: 'Cancel' })).not.toBeInTheDocument()
  })

  it("disables only that order's buttons while the change is in flight", async () => {
    let finish
    api.setStatus.mockReturnValue(
      new Promise((resolve) => {
        finish = resolve
      }),
    )
    await renderDashboard(board([entry('o1', '101', 'PLACED'), entry('o2', '102', 'PLACED')]))

    await user().click(within(card('101')).getByRole('button', { name: 'Start' }))

    expect(within(card('101')).getByRole('button', { name: 'Start' })).toBeDisabled()
    expect(within(card('101')).getByRole('button', { name: 'Cancel' })).toBeDisabled()
    expect(within(card('102')).getByRole('button', { name: 'Start' })).toBeEnabled()

    finish({})
    await flush()
    expect(within(card('101')).getByRole('button', { name: 'Start' })).toBeEnabled()
  })

  it('shows the error when the change fails, and clears it on the next attempt', async () => {
    api.setStatus.mockRejectedValueOnce(new ApiError('Order is READY and can’t move to CANCELLED', 409))
    await renderDashboard()
    const u = user()

    await u.click(button('Start'))
    await flush()
    expect(screen.getByRole('alert')).toHaveTextContent('Order is READY and can’t move to CANCELLED')
    expect(button('Start')).toBeEnabled()

    await u.click(button('Start'))
    await flush()
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('signs staff out, without an error banner, when the session has expired', async () => {
    api.setStatus.mockRejectedValue(new ApiError('Staff sign-in required', 401))
    const { onSignOut } = await renderDashboard()

    await user().click(button('Start'))
    await flush()

    expect(onSignOut).toHaveBeenCalledTimes(1)
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })
})

describe('Dashboard: baristas on the bar (changeBaristas)', () => {
  it('adds and removes one barista at a time', async () => {
    await renderDashboard(board([], 3))
    const u = user()

    await u.click(button('More baristas'))
    await u.click(button('Fewer baristas'))

    expect(api.setBaristas).toHaveBeenNthCalledWith(1, TOKEN, 4)
    expect(api.setBaristas).toHaveBeenNthCalledWith(2, TOKEN, 2)
  })

  it('never goes below one or above eight', async () => {
    const { unmount } = await renderDashboard(board([], 1))
    await user().click(button('Fewer baristas'))
    expect(api.setBaristas).toHaveBeenLastCalledWith(TOKEN, 1)
    unmount()

    await renderDashboard(board([], 8))
    await user().click(button('More baristas'))
    expect(api.setBaristas).toHaveBeenLastCalledWith(TOKEN, 8)
  })

  it('assumes one barista before the first board arrives', async () => {
    await renderDashboard(null)

    await user().click(button('More baristas'))

    expect(api.setBaristas).toHaveBeenCalledWith(TOKEN, 2)
  })

  it('shows the error when the change fails', async () => {
    api.setBaristas.mockRejectedValue(new ApiError('Café server is down', 503))
    await renderDashboard()

    await user().click(button('More baristas'))
    await flush()

    expect(screen.getByRole('alert')).toHaveTextContent('Café server is down')
  })

  it('signs staff out when the session has expired', async () => {
    api.setBaristas.mockRejectedValue(new ApiError('Staff sign-in required', 401))
    const { onSignOut } = await renderDashboard()

    await user().click(button('More baristas'))
    await flush()

    expect(onSignOut).toHaveBeenCalledTimes(1)
  })
})

describe('Dashboard: staying up to date', () => {
  it('shows the live indicator once the stream has delivered', async () => {
    await renderDashboard(null)
    expect(screen.getByText('Auto-refresh')).toBeInTheDocument()

    await push(board([]))

    expect(screen.getByText('Live')).toBeInTheDocument()
  })

  it('moves an order between columns when the stream pushes a new board', async () => {
    await renderDashboard(board([entry('o1', '101', 'PLACED')]))
    expect(within(column('New')).getByText('#101')).toBeInTheDocument()

    await push(board([entry('o1', '101', 'PREPARING')]))

    expect(within(column('Brewing')).getByText('#101')).toBeInTheDocument()
    expect(within(column('New')).queryByText('#101')).not.toBeInTheDocument()
  })

  it('polls the board every three seconds until the stream delivers, then stops', async () => {
    api.staffBoard.mockResolvedValue(board([entry('o1', '101', 'PLACED')]))
    await renderDashboard(null)

    await advance(3000)
    expect(api.staffBoard).toHaveBeenCalledTimes(1)
    expect(screen.getByText('#101')).toBeInTheDocument()

    await push(board([entry('o1', '101', 'PLACED')]))
    await advance(9000)
    expect(api.staffBoard).toHaveBeenCalledTimes(1)
  })

  it('signs staff out when polling finds the session expired', async () => {
    api.staffBoard.mockRejectedValue(new ApiError('Staff sign-in required', 401))
    const { onSignOut } = await renderDashboard(null)

    await advance(3000)

    expect(onSignOut).toHaveBeenCalledTimes(1)
  })

  describe('when the stream closes', () => {
    it('reconnects after three seconds if the session is still valid', async () => {
      await renderDashboard()

      await act(() => FakeEventSource.last.fail({ closed: true }))
      await advance(3000)

      expect(api.staffBoard).toHaveBeenCalledWith(TOKEN)
      expect(FakeEventSource.instances).toHaveLength(2)
    })

    it('signs staff out instead of reconnecting if the token has expired', async () => {
      api.staffBoard.mockRejectedValue(new ApiError('Staff sign-in required', 401))
      const { onSignOut } = await renderDashboard()

      await act(() => FakeEventSource.last.fail({ closed: true }))
      await advance(3000)

      expect(onSignOut).toHaveBeenCalled()
      expect(FakeEventSource.instances).toHaveLength(1)
    })

    it('keeps trying to reconnect when the server is merely unreachable', async () => {
      api.staffBoard.mockRejectedValue(new ApiError('Can’t reach the café server', 0))
      const { onSignOut } = await renderDashboard()

      await act(() => FakeEventSource.last.fail({ closed: true }))
      await advance(3000)

      expect(onSignOut).not.toHaveBeenCalled()
      expect(FakeEventSource.instances).toHaveLength(2)
    })

    it('does not reconnect after a recoverable error', async () => {
      await renderDashboard()

      await act(() => FakeEventSource.last.fail())
      await advance(5000)

      expect(FakeEventSource.instances).toHaveLength(1)
    })
  })

  it('closes the stream and stops polling when staff leave the page', async () => {
    const { unmount } = await renderDashboard(null)
    const stream = FakeEventSource.last

    unmount()
    await advance(10000)

    expect(stream.closed).toBe(true)
    expect(api.staffBoard).not.toHaveBeenCalled()
  })

  it('signs out from the header button', async () => {
    const { onSignOut } = await renderDashboard()

    await user().click(button('Sign out'))

    expect(onSignOut).toHaveBeenCalledTimes(1)
  })
})
