import { describe, expect, it, vi } from 'vitest'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { api } from '../api.js'
import MenuTab from './MenuTab.jsx'

const session = { id: 's1', name: 'Asha' }

const espresso = {
  id: 'espresso',
  name: 'Espresso',
  category: 'Espresso bar',
  description: 'Double shot',
  price: 160,
  tags: ['hot', 'vegan'],
}
const coldBrew = {
  id: 'cold-brew',
  name: 'Cold Brew',
  category: 'Cold coffee',
  description: 'Slow steeped',
  price: 240,
  tags: ['cold'],
}
const toast = { id: 'toast', name: 'Toast', category: 'Bakery & bites', description: 'Buttered', price: 320, tags: [] }
const menu = [espresso, coldBrew, toast]

function setup({ picks, cartQty = () => 0 } = {}) {
  vi.spyOn(api, 'recommendations').mockImplementation(() => picks ?? new Promise(() => {}))
  const onAdd = vi.fn()
  render(<MenuTab session={session} menu={menu} onAdd={onAdd} cartQty={cartQty} />)
  return { onAdd, menuSection: screen.getByRole('region', { name: 'Menu' }) }
}

describe('MenuTab', () => {
  it('lists every item with its price and description', () => {
    const { menuSection } = setup()

    for (const item of menu) {
      expect(within(menuSection).getByRole('heading', { name: item.name })).toBeInTheDocument()
      expect(within(menuSection).getByText(item.description)).toBeInTheDocument()
    }
    expect(within(menuSection).getByText('₹160')).toBeInTheDocument()
  })

  it('shows only friendly dietary labels as tags', () => {
    const { menuSection } = setup()

    expect(within(menuSection).getByText('Vegan')).toBeInTheDocument()
    expect(within(menuSection).queryByText('hot')).not.toBeInTheDocument()
    expect(within(menuSection).queryByText('cold')).not.toBeInTheDocument()
  })

  it('filters by category and can return to all', async () => {
    const { menuSection } = setup()
    const user = userEvent.setup()

    await user.click(within(menuSection).getByRole('button', { name: 'Cold coffee' }))
    expect(within(menuSection).getByRole('button', { name: 'Cold coffee' })).toHaveAttribute('aria-pressed', 'true')
    expect(within(menuSection).getByText('Cold Brew')).toBeInTheDocument()
    expect(within(menuSection).queryByText('Espresso')).not.toBeInTheDocument()

    await user.click(within(menuSection).getByRole('button', { name: 'All' }))
    expect(within(menuSection).getAllByRole('listitem')).toHaveLength(3)
  })

  it('adds the item the guest taps', async () => {
    const { onAdd, menuSection } = setup()

    await userEvent.click(within(menuSection).getByRole('button', { name: 'Add Toast' }))

    expect(onAdd).toHaveBeenCalledWith(toast)
  })

  it('shows how many of an item are already in the cart', () => {
    const { menuSection } = setup({ cartQty: (id) => (id === 'espresso' ? 3 : 0) })

    expect(within(menuSection).getByText('3')).toBeInTheDocument()
    expect(within(menuSection).getAllByText(/^\d+$/)).toHaveLength(1)
  })

  describe('Brew picks', () => {
    it('asks for recommendations for this guest and shows a loading state', () => {
      setup()

      expect(api.recommendations).toHaveBeenCalledWith('s1')
      expect(screen.getByText(/reading the menu for you/i)).toBeInTheDocument()
      expect(screen.getByText(/picked by brew for asha/i)).toBeInTheDocument()
    })

    it('shows the headline, each pick and its reason', async () => {
      const { onAdd } = setup({
        picks: Promise.resolve({
          headline: 'Something bold today',
          picks: [{ item: espresso, reason: 'Matches your taste' }],
        }),
      })

      expect(await screen.findByRole('heading', { name: 'Something bold today' })).toBeInTheDocument()
      const picked = within(screen.getByRole('region', { name: 'Picked for you' }))
      expect(picked.getByText('Matches your taste')).toBeInTheDocument()

      await userEvent.click(picked.getByRole('button', { name: 'Add Espresso' }))
      expect(onAdd).toHaveBeenCalledWith(espresso)
    })

    it('degrades gracefully when recommendations fail, keeping the full menu', async () => {
      const { menuSection } = setup({ picks: Promise.reject(new Error('AI down')) })

      expect(await screen.findByText(/recommendations are unavailable/i)).toBeInTheDocument()
      expect(within(menuSection).getAllByRole('listitem')).toHaveLength(3)
    })
  })
})
