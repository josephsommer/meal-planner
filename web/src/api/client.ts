export class ApiError extends Error {
  constructor(
    public readonly status: number,
    message: string,
  ) {
    super(message)
    this.name = 'ApiError'
  }
}

function getXsrfCookie(): string | null {
  const match = document.cookie.match(/XSRF-TOKEN=([^;]+)/)
  return match ? decodeURIComponent(match[1]) : null
}

// Empty by default, which keeps every path relative: in local dev that's what
// the Vite proxy (vite.config.ts) expects, and in tests it's what the
// fetch mocks expect. The deploy workflow sets VITE_API_BASE_URL at build
// time, since the deployed frontend (S3) and API (EB) are on different
// origins with no proxy between them.
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? ''

export async function apiFetch(path: string, init: RequestInit = {}): Promise<Response> {
  const method = (init.method ?? 'GET').toUpperCase()
  const headers = new Headers(init.headers)

  if (method !== 'GET' && method !== 'HEAD') {
    const token = getXsrfCookie()
    if (token) headers.set('X-XSRF-TOKEN', token)
  }

  return fetch(`${API_BASE_URL}${path}`, { ...init, headers, credentials: 'include' })
}

export async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await apiFetch(path, init)
  if (!res.ok) {
    throw new ApiError(res.status, `${init?.method ?? 'GET'} ${path} failed with ${res.status}`)
  }
  const text = await res.text()
  return (text ? JSON.parse(text) : undefined) as T
}
