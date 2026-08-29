const TOKEN_KEY = 'happy4j.auth.token'

export const authToken = {
  get: () => localStorage.getItem(TOKEN_KEY) || '',
  set: (value) => localStorage.setItem(TOKEN_KEY, value),
  clear: () => localStorage.removeItem(TOKEN_KEY),
}

async function request(path, options = {}) {
  const headers = new Headers(options.headers || {})
  if (options.body && !headers.has('Content-Type')) headers.set('Content-Type', 'application/json')
  if (authToken.get()) headers.set('Authorization', `Bearer ${authToken.get()}`)
  const response = await fetch(path, { ...options, headers })
  const text = await response.text()
  let body
  try {
    body = text ? JSON.parse(text) : null
  } catch {
    body = text
  }
  if (!response.ok) throw new Error(body?.message || body?.error || `请求失败 (${response.status})`)
  return body
}

export const authRequest = (path, values) => request(path, { method: 'POST', body: JSON.stringify(values) })

export async function currentUser() {
  if (!authToken.get()) return null
  try {
    return await request('/api/auth/me')
  } catch {
    authToken.clear()
    return null
  }
}
