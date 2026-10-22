import { afterEach, describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { api, ApiError } from '../api.js'
import Welcome from './Welcome.jsx'

const session = { id: 's1', name: 'Asha', table: '7', preferences: ['vegan'] }

afterEach(() => window.history.replaceState({}, '', '/'))

function setup() {
  const onStart = vi.fn()
  render(<Welcome onStart={onStart} />)
  return { onStart, user: userEvent.setup() }
}

describe('Welcome', () => {
  it('prefills the table from the QR link', () => {
    window.history.replaceState({}, '', '/?table=12')
    setup()

    expect(screen.getByLabelText('Table')).toHaveValue('12')
  })

  it('asks for a name before talking to the server', async () => {
    const create = vi.spyOn(api, 'createSession')
    const { user } = setup()
    await user.type(screen.getByLabelText('Table'), '7')

    await user.click(screen.getByRole('button', { name: /start ordering/i }))

    expect(screen.getByRole('alert')).toHaveTextContent(/add your name/i)
    expect(create).not.toHaveBeenCalled()
  })

  it('asks for a table before talking to the server', async () => {
    const create = vi.spyOn(api, 'createSession')
    const { user } = setup()
    await user.type(screen.getByLabelText('Your name'), 'Asha')

    await user.click(screen.getByRole('button', { name: /start ordering/i }))

    expect(screen.getByRole('alert')).toHaveTextContent(/table number/i)
    expect(create).not.toHaveBeenCalled()
  })

  it('creates the session with trimmed details and the chosen preferences', async () => {
    const create = vi.spyOn(api, 'createSession').mockResolvedValue(session)
    const { onStart, user } = setup()
    await user.type(screen.getByLabelText('Your name'), '  Asha ')
    await user.type(screen.getByLabelText('Table'), ' 7 ')
    await user.click(screen.getByRole('button', { name: 'Vegan' }))
    await user.click(screen.getByRole('button', { name: 'Prefer cold' }))

    await user.click(screen.getByRole('button', { name: /start ordering/i }))

    expect(create).toHaveBeenCalledWith('Asha', '7', ['vegan', 'cold'])
    expect(onStart).toHaveBeenCalledWith(session)
  })

  it('toggles a preference on and off', async () => {
    const { user } = setup()
    const vegan = screen.getByRole('button', { name: 'Vegan' })
    expect(vegan).toHaveAttribute('aria-pressed', 'false')

    await user.click(vegan)
    expect(vegan).toHaveAttribute('aria-pressed', 'true')

    await user.click(vegan)
    expect(vegan).toHaveAttribute('aria-pressed', 'false')
  })

  it('shows the server error and lets the guest try again', async () => {
    vi.spyOn(api, 'createSession').mockRejectedValue(new ApiError('Café is closed', 503))
    const { onStart, user } = setup()
    await user.type(screen.getByLabelText('Your name'), 'Asha')
    await user.type(screen.getByLabelText('Table'), '7')

    await user.click(screen.getByRole('button', { name: /start ordering/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Café is closed')
    expect(onStart).not.toHaveBeenCalled()
    expect(screen.getByRole('button', { name: /start ordering/i })).toBeEnabled()
  })

  it('disables the button while the session is being created', async () => {
    vi.spyOn(api, 'createSession').mockReturnValue(new Promise(() => {}))
    const { user } = setup()
    await user.type(screen.getByLabelText('Your name'), 'Asha')
    await user.type(screen.getByLabelText('Table'), '7')

    await user.click(screen.getByRole('button', { name: /start ordering/i }))

    expect(screen.getByRole('button', { name: /start ordering/i })).toBeDisabled()
  })
})
