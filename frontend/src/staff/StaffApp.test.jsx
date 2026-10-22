import { beforeEach, describe, expect, it, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { api, ApiError, store } from '../api.js'
import StaffApp from './StaffApp.jsx'

// The dashboard opens live connections; its own behaviour is not under test here.
vi.mock('./Dashboard.jsx', () => ({
  default: ({ token, onSignOut }) => (
    <div>
      <p>Dashboard for {token}</p>
      <button onClick={onSignOut}>Sign out</button>
    </div>
  ),
}))

beforeEach(() => localStorage.clear())

describe('StaffApp', () => {
  it('shows the PIN screen when there is no saved token', () => {
    render(<StaffApp />)

    expect(screen.getByLabelText('Staff PIN')).toBeInTheDocument()
  })

  it('skips the PIN screen when a token is already saved', () => {
    store.set('gc.staffToken', 'saved-token')

    render(<StaffApp />)

    expect(screen.getByText('Dashboard for saved-token')).toBeInTheDocument()
  })

  it('does not call the server for an empty PIN', async () => {
    const login = vi.spyOn(api, 'staffLogin')
    render(<StaffApp />)

    await userEvent.click(screen.getByRole('button', { name: /open dashboard/i }))

    expect(screen.getByRole('alert')).toHaveTextContent(/enter the café pin/i)
    expect(login).not.toHaveBeenCalled()
  })

  it('stores the token and opens the dashboard after a good PIN', async () => {
    const login = vi.spyOn(api, 'staffLogin').mockResolvedValue({ token: 'fresh-token' })
    const user = userEvent.setup()
    render(<StaffApp />)

    await user.type(screen.getByLabelText('Staff PIN'), '4321')
    await user.click(screen.getByRole('button', { name: /open dashboard/i }))

    expect(login).toHaveBeenCalledWith('4321')
    expect(await screen.findByText('Dashboard for fresh-token')).toBeInTheDocument()
    expect(store.get('gc.staffToken')).toBe('fresh-token')
  })

  it('shows the error and clears the PIN after a wrong one', async () => {
    vi.spyOn(api, 'staffLogin').mockRejectedValue(new ApiError('Wrong PIN', 401))
    const user = userEvent.setup()
    render(<StaffApp />)

    await user.type(screen.getByLabelText('Staff PIN'), '0000')
    await user.click(screen.getByRole('button', { name: /open dashboard/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Wrong PIN')
    expect(screen.getByLabelText('Staff PIN')).toHaveValue('')
    expect(store.get('gc.staffToken')).toBeNull()
  })

  it('signing out forgets the token and returns to the PIN screen', async () => {
    store.set('gc.staffToken', 'saved-token')
    render(<StaffApp />)

    await userEvent.click(screen.getByRole('button', { name: 'Sign out' }))

    expect(screen.getByLabelText('Staff PIN')).toBeInTheDocument()
    expect(store.get('gc.staffToken')).toBeNull()
  })
})
