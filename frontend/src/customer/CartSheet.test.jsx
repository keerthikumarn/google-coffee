import { afterEach, describe, expect, it, vi } from 'vitest'
import { act, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { api } from '../api.js'
import CartSheet from './CartSheet.jsx'

const espresso = { id: 'espresso', name: 'Espresso', price: 160 }
const toast = { id: 'toast', name: 'Toast', price: 320 }

function props(overrides = {}) {
  return {
    cart: [
      { item: espresso, qty: 2, note: '' },
      { item: toast, qty: 1, note: '' },
    ],
    setQty: vi.fn(),
    setNote: vi.fn(),
    onClose: vi.fn(),
    onPlace: vi.fn(),
    placing: false,
    error: '',
    ...overrides,
  }
}

afterEach(() => vi.useRealTimers())

describe('CartSheet', () => {
  it('shows an empty state and does not ask for an estimate', () => {
    const estimate = vi.spyOn(api, 'estimate')
    render(<CartSheet {...props({ cart: [] })} />)

    expect(screen.getByText(/nothing here yet/i)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /place order/i })).not.toBeInTheDocument()
    expect(estimate).not.toHaveBeenCalled()
  })

  it('lists the items and totals the order', () => {
    vi.spyOn(api, 'estimate').mockReturnValue(new Promise(() => {}))
    render(<CartSheet {...props()} />)

    expect(screen.getByText('Espresso')).toBeInTheDocument()
    expect(screen.getByText('Toast')).toBeInTheDocument()
    expect(screen.getByText('₹640')).toBeInTheDocument()
  })

  it('changes quantities one at a time', async () => {
    vi.spyOn(api, 'estimate').mockReturnValue(new Promise(() => {}))
    const p = props()
    render(<CartSheet {...p} />)

    await userEvent.click(screen.getByRole('button', { name: 'Add one Espresso' }))
    await userEvent.click(screen.getByRole('button', { name: 'Remove one Espresso' }))

    expect(p.setQty).toHaveBeenNthCalledWith(1, 'espresso', 3)
    expect(p.setQty).toHaveBeenNthCalledWith(2, 'espresso', 1)
  })

  it('stops the plus button at ten', () => {
    vi.spyOn(api, 'estimate').mockReturnValue(new Promise(() => {}))
    render(<CartSheet {...props({ cart: [{ item: espresso, qty: 10, note: '' }] })} />)

    expect(screen.getByRole('button', { name: 'Add one Espresso' })).toBeDisabled()
  })

  it('passes barista notes up per item', async () => {
    vi.spyOn(api, 'estimate').mockReturnValue(new Promise(() => {}))
    const p = props({ cart: [{ item: espresso, qty: 1, note: '' }] })
    render(<CartSheet {...p} />)

    await userEvent.type(screen.getByLabelText('Note for Espresso'), 'x')

    expect(p.setNote).toHaveBeenCalledWith('espresso', 'x')
  })

  it('closes from the close button and the backdrop, but not from inside the sheet', async () => {
    vi.spyOn(api, 'estimate').mockReturnValue(new Promise(() => {}))
    const p = props()
    const { container } = render(<CartSheet {...p} />)

    await userEvent.click(screen.getByText('Your order'))
    expect(p.onClose).not.toHaveBeenCalled()

    await userEvent.click(screen.getByRole('button', { name: 'Close' }))
    await userEvent.click(container.firstChild)
    expect(p.onClose).toHaveBeenCalledTimes(2)
  })

  it('places the order, and blocks double submission while placing', async () => {
    vi.spyOn(api, 'estimate').mockReturnValue(new Promise(() => {}))
    const p = props()
    const { rerender } = render(<CartSheet {...p} />)

    await userEvent.click(screen.getByRole('button', { name: /place order/i }))
    expect(p.onPlace).toHaveBeenCalledTimes(1)

    rerender(<CartSheet {...p} placing />)
    expect(screen.getByRole('button', { name: /place order/i })).toBeDisabled()
  })

  it('shows an error from the server', () => {
    vi.spyOn(api, 'estimate').mockReturnValue(new Promise(() => {}))
    render(<CartSheet {...props({ error: 'Unknown menu item: toast' })} />)

    expect(screen.getByRole('alert')).toHaveTextContent('Unknown menu item: toast')
  })

  describe('wait-time estimate', () => {
    it('is requested after a short pause and then shown', async () => {
      vi.useFakeTimers()
      const estimate = vi.spyOn(api, 'estimate').mockResolvedValue({ minutes: 12 })
      render(<CartSheet {...props()} />)
      expect(screen.getByText(/checking the queue/i)).toBeInTheDocument()
      expect(estimate).not.toHaveBeenCalled()

      await act(() => vi.advanceTimersByTimeAsync(300))

      expect(estimate).toHaveBeenCalledWith([
        { itemId: 'espresso', qty: 2 },
        { itemId: 'toast', qty: 1 },
      ])
      expect(screen.getByText('12 min')).toBeInTheDocument()
    })

    it('waits for the cart to settle, asking once for rapid changes', async () => {
      vi.useFakeTimers()
      const estimate = vi.spyOn(api, 'estimate').mockResolvedValue({ minutes: 5 })
      const { rerender } = render(<CartSheet {...props({ cart: [{ item: espresso, qty: 1, note: '' }] })} />)

      await act(() => vi.advanceTimersByTimeAsync(100))
      rerender(<CartSheet {...props({ cart: [{ item: espresso, qty: 2, note: '' }] })} />)
      await act(() => vi.advanceTimersByTimeAsync(300))

      expect(estimate).toHaveBeenCalledTimes(1)
      expect(estimate).toHaveBeenCalledWith([{ itemId: 'espresso', qty: 2 }])
    })

    it('does not re-request when only a note changes', async () => {
      vi.useFakeTimers()
      const estimate = vi.spyOn(api, 'estimate').mockResolvedValue({ minutes: 5 })
      const { rerender } = render(<CartSheet {...props({ cart: [{ item: espresso, qty: 1, note: '' }] })} />)
      await act(() => vi.advanceTimersByTimeAsync(300))

      rerender(<CartSheet {...props({ cart: [{ item: espresso, qty: 1, note: 'extra hot' }] })} />)
      await act(() => vi.advanceTimersByTimeAsync(300))

      expect(estimate).toHaveBeenCalledTimes(1)
    })

    it('falls back to the waiting message when the estimate fails', async () => {
      vi.useFakeTimers()
      vi.spyOn(api, 'estimate').mockRejectedValue(new Error('down'))
      render(<CartSheet {...props()} />)

      await act(() => vi.advanceTimersByTimeAsync(300))

      expect(screen.getByText(/checking the queue/i)).toBeInTheDocument()
    })
  })
})
