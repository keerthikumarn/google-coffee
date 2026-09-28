async function request(path, { method = 'GET', body, token } = {}) {
  const headers = { 'Content-Type': 'application/json' }
  if (token) headers['X-Staff-Token'] = token
  let res
  try {
    res = await fetch(path, { method, headers, body: body ? JSON.stringify(body) : undefined })
  } catch {
    throw new ApiError('Can’t reach the café server. Check your connection and try again.', 0)
  }
  const text = await res.text()
  const data = text ? safeJson(text) : null
  if (!res.ok) throw new ApiError(data?.message || `Request failed (${res.status})`, res.status)
  return data
}

function safeJson(text) {
  try { return JSON.parse(text) } catch { return null }
}

export class ApiError extends Error {
  constructor(message, status) { super(message); this.status = status }
}

export const api = {
  menu: () => request('/api/menu'),
  cafeStatus: () => request('/api/cafe/status'),
  createSession: (name, table, preferences) => request('/api/sessions', { method: 'POST', body: { name, table, preferences } }),
  session: (id) => request(`/api/sessions/${id}`),
  updatePreferences: (id, preferences) => request(`/api/sessions/${id}/preferences`, { method: 'PUT', body: { preferences } }),
  chat: (sessionId, message, history) => request('/api/chat', { method: 'POST', body: { sessionId, message, history } }),
  recommendations: (sessionId) => request(`/api/recommendations?sessionId=${encodeURIComponent(sessionId)}`),
  estimate: (items) => request('/api/orders/estimate', { method: 'POST', body: { items } }),
  placeOrder: (sessionId, items) => request('/api/orders', { method: 'POST', body: { sessionId, items } }),
  order: (id, sessionId) => request(`/api/orders/${id}?sessionId=${encodeURIComponent(sessionId)}`),
  feedback: (sessionId, orderId, rating, comment) => request('/api/feedback', { method: 'POST', body: { sessionId, orderId, rating, comment } }),

  staffLogin: (pin) => request('/api/staff/login', { method: 'POST', body: { pin } }),
  staffBoard: (token) => request('/api/staff/board', { token }),
  setStatus: (token, id, status) => request(`/api/staff/orders/${id}`, { method: 'PATCH', token, body: { status } }),
  setBaristas: (token, activeBaristas) => request('/api/staff/settings', { method: 'PUT', token, body: { activeBaristas } }),
  pulse: (token, refresh = false) => request(`/api/staff/pulse?refresh=${refresh}`, { token }),
}

export const store = {
  get(key) { try { return JSON.parse(localStorage.getItem(key)) } catch { return null } },
  set(key, value) { try { localStorage.setItem(key, JSON.stringify(value)) } catch { /* storage unavailable */ } },
  remove(key) { try { localStorage.removeItem(key) } catch { /* storage unavailable */ } },
}

export const rupees = (n) => `₹${Number(n || 0).toLocaleString('en-IN')}`