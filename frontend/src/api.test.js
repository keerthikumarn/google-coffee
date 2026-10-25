import { beforeEach, describe, expect, it, vi } from 'vitest'
import { api, ApiError, rupees, store } from './api.js'

function respond(status, body) {
  const text = body === undefined ? '' : typeof body === 'string' ? body : JSON.stringify(body)
  return { ok: status >= 200 && status < 300, status, text: async () => text }
}

function mockFetch(response) {
  const fn = vi.fn().mockResolvedValue(response)
  vi.stubGlobal('fetch', fn)
  return fn
}

describe('request helper (through api)', () => {
  beforeEach(() => vi.unstubAllGlobals())

  it('sends JSON and returns the parsed body', async () => {
    const fetch = mockFetch(respond(200, [{ id: 'espresso' }]))

    const menu = await api.menu()

    expect(menu).toEqual([{ id: 'espresso' }])
    expect(fetch).toHaveBeenCalledWith('/api/menu', {
      method: 'GET',
      headers: { 'Content-Type': 'application/json' },
      body: undefined,
    })
  })

  it('serialises the body of POST requests', async () => {
    const fetch = mockFetch(respond(200, { id: 's1' }))

    await api.createSession('Asha', '7', ['vegan'])

    const [path, init] = fetch.mock.calls[0]
    expect(path).toBe('/api/sessions')
    expect(init.method).toBe('POST')
    expect(JSON.parse(init.body)).toEqual({ name: 'Asha', table: '7', preferences: ['vegan'] })
  })

  it('sends the staff token header only for staff calls', async () => {
    const fetch = mockFetch(respond(200, {}))

    await api.staffBoard('tok-123')
    await api.menu()

    expect(fetch.mock.calls[0][1].headers['X-Staff-Token']).toBe('tok-123')
    expect(fetch.mock.calls[1][1].headers).not.toHaveProperty('X-Staff-Token')
  })

  it('returns null for an empty response body', async () => {
    mockFetch(respond(204))

    expect(await api.setStatus('tok', 'o1', 'READY')).toBeNull()
  })

  it('throws an ApiError carrying the server message and status', async () => {
    mockFetch(respond(409, { message: 'Order is READY and can’t move to CANCELLED' }))

    const error = await api.setStatus('tok', 'o1', 'CANCELLED').catch((e) => e)

    expect(error).toBeInstanceOf(ApiError)
    expect(error.status).toBe(409)
    expect(error.message).toBe('Order is READY and can’t move to CANCELLED')
  })

  it('falls back to a generic message when the error body is not JSON', async () => {
    mockFetch(respond(502, '<html>Bad gateway</html>'))

    const error = await api.menu().catch((e) => e)

    expect(error.message).toBe('Request failed (502)')
    expect(error.status).toBe(502)
  })

  it('reports an unreachable server as status 0 with a friendly message', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')))

    const error = await api.menu().catch((e) => e)

    expect(error).toBeInstanceOf(ApiError)
    expect(error.status).toBe(0)
    expect(error.message).toMatch(/can’t reach the café server/i)
  })
})

describe('endpoint shapes', () => {
  beforeEach(() => vi.unstubAllGlobals())

  it.each([
    ['recommendations', () => api.recommendations('a b&c'), '/api/recommendations?sessionId=a%20b%26c'],
    ['order', () => api.order('o1', 'a b'), '/api/orders/o1?sessionId=a%20b'],
    ['pulse refresh', () => api.pulse('tok', true), '/api/staff/pulse?refresh=true'],
    ['pulse cached', () => api.pulse('tok'), '/api/staff/pulse?refresh=false'],
  ])('%s builds a safely encoded URL', async (_name, call, expected) => {
    const fetch = mockFetch(respond(200, {}))

    await call()

    expect(fetch.mock.calls[0][0]).toBe(expected)
  })

  it('updates order status with PATCH and the new status', async () => {
    const fetch = mockFetch(respond(200, {}))

    await api.setStatus('tok', 'o1', 'PREPARING')

    const [path, init] = fetch.mock.calls[0]
    expect(path).toBe('/api/staff/orders/o1')
    expect(init.method).toBe('PATCH')
    expect(JSON.parse(init.body)).toEqual({ status: 'PREPARING' })
  })
})

describe('cancelOrder', () => {
  beforeEach(() => vi.unstubAllGlobals())

  it('posts to the cancel endpoint with the encoded ids and no body', async () => {
    const fetch = mockFetch(respond(200, {}))

    await api.cancelOrder('o 1', 'a b&c')

    const [path, init] = fetch.mock.calls[0]
    expect(path).toBe('/api/orders/o%201/cancel?sessionId=a%20b%26c')
    expect(init.method).toBe('POST')
    expect(init.body).toBeUndefined()
  })

  it('returns the cancelled order view', async () => {
    mockFetch(respond(200, { order: { id: 'o1', status: 'CANCELLED' }, etaMinutes: 0, queuePosition: 0 }))

    const view = await api.cancelOrder('o1', 's1')

    expect(view.order.status).toBe('CANCELLED')
  })

  it('reports why the server refused, with its status', async () => {
    mockFetch(respond(409, { message: 'This order is already preparing, so it can’t be cancelled here.' }))

    const error = await api.cancelOrder('o1', 's1').catch((e) => e)

    expect(error).toBeInstanceOf(ApiError)
    expect(error.status).toBe(409)
    expect(error.message).toMatch(/already preparing/)
  })

  it('reports an order that is not the guest’s as not found', async () => {
    mockFetch(respond(404, { message: 'Order not found' }))

    const error = await api.cancelOrder('o1', 'someone-else').catch((e) => e)

    expect(error.status).toBe(404)
  })
})

describe('store (localStorage wrapper)', () => {
  beforeEach(() => localStorage.clear())

  it('round-trips JSON values', () => {
    store.set('prefs', { a: [1, 2] })

    expect(store.get('prefs')).toEqual({ a: [1, 2] })
  })

  it('returns null for missing or corrupt entries', () => {
    expect(store.get('missing')).toBeNull()
    localStorage.setItem('bad', '{not json')
    expect(store.get('bad')).toBeNull()
  })

  it('removes entries', () => {
    store.set('k', 1)
    store.remove('k')

    expect(store.get('k')).toBeNull()
  })

  it('never throws when storage is unavailable', () => {
    const broken = () => {
      throw new Error('storage blocked')
    }
    vi.stubGlobal('localStorage', { getItem: broken, setItem: broken, removeItem: broken })

    expect(() => store.set('k', 1)).not.toThrow()
    expect(() => store.remove('k')).not.toThrow()
    expect(store.get('k')).toBeNull()
    vi.unstubAllGlobals()
  })
})

describe('rupees', () => {
  it('formats with the rupee sign and Indian digit grouping', () => {
    expect(rupees(160)).toBe('₹160')
    expect(rupees(123456)).toBe('₹1,23,456')
  })

  it('treats missing values as zero', () => {
    expect(rupees(undefined)).toBe('₹0')
    expect(rupees(null)).toBe('₹0')
  })
})
