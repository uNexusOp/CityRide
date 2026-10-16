const API = '/api'

export async function request(path, options = {}, token = null) {
  const response = await fetch(`${API}${path}`, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...options.headers,
    },
  })
  const contentType = response.headers.get('content-type') ?? ''
  const data = contentType.includes('application/json') ? await response.json() : null
  if (!response.ok) throw new Error(data?.message || `Request failed (${response.status})`)
  return data
}

export const api = {
  me: (token) => request('/auth/me', {}, token),
  login: (email, password) => request('/auth/login', {
    method: 'POST',
    body: JSON.stringify({ email, password }),
  }),
  register: (fullName, email, password, role) => request('/auth/register', {
    method: 'POST',
    body: JSON.stringify({ fullName, email, password, role }),
  }),
  mine: (token) => request('/rides/mine', {}, token),
  available: (token) => request('/rides/available', {}, token),
  decline: (token, id) => request(`/rides/${id}/decline`, { method: 'POST' }, token),
  availability: (token, available) => request('/rides/driver/availability', {
    method: 'POST',
    body: JSON.stringify({ available }),
  }, token),
  estimate: (token, distanceKm, promoCode = '') => request(
    `/rides/estimate?distanceKm=${encodeURIComponent(distanceKm)}&promoCode=${encodeURIComponent(promoCode)}`,
    {},
    token,
  ),
  adminSummary: (token) => request('/admin/summary', {}, token),
  adminRides: (token) => request('/admin/rides', {}, token),
  driverRankings: (token) => request('/drivers/rankings', {}, token),
  book: (token, trip) => request('/rides', {
    method: 'POST',
    body: JSON.stringify(trip),
  }, token),
  accept: (token, id) => request(`/rides/${id}/accept`, { method: 'POST' }, token),
  status: (token, id, status) => request(`/rides/${id}/status`, {
    method: 'POST',
    body: JSON.stringify({ status }),
  }, token),
  pay: (token, id) => request(`/rides/${id}/pay`, { method: 'POST' }, token),
  receipt: (token, id) => request(`/rides/${id}/receipt`, {}, token),
  emailReceipt: (token, id) => request(`/rides/${id}/receipt/email`, { method: 'POST' }, token),
  sos: (token, id) => request(`/rides/${id}/sos`, { method: 'POST' }, token),
  rate: (token, id, rating, review) => request(`/rides/${id}/rating`, {
    method: 'POST',
    body: JSON.stringify({ rating, review }),
  }, token),
  createSupportTicket: (token, ticket) => request('/support/tickets', {
    method: 'POST',
    body: JSON.stringify(ticket),
  }, token),
  supportTickets: (token) => request('/support/tickets/admin', {}, token),
  updateSupportTicket: (token, id, status) => request(`/support/tickets/admin/${id}`, {
    method: 'PATCH',
    body: JSON.stringify({ status }),
  }, token),
  promoCodes: (token) => request('/admin/promo-codes', {}, token),
  createPromoCode: (token, promo) => request('/admin/promo-codes', {
    method: 'POST',
    body: JSON.stringify(promo),
  }, token),
  setPromoEnabled: (token, code, enabled) => request(
    `/admin/promo-codes/${encodeURIComponent(code)}/enabled`,
    { method: 'PATCH', body: JSON.stringify({ enabled }) },
    token,
  ),
}
