import { afterEach, describe, expect, it, vi } from 'vitest'
import { apiFetch, ApiError, request } from './client'

function mockResponse(overrides: Partial<Response> & { text?: () => Promise<string> }) {
  return { ok: true, status: 200, text: () => Promise.resolve(''), ...overrides } as Response
}

describe('apiFetch', () => {
  afterEach(() => {
    document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT'
  })

  it('attaches X-XSRF-TOKEN on a mutating request when the cookie is present', async () => {
    document.cookie = 'XSRF-TOKEN=abc123'
    const fetchMock = vi.fn().mockResolvedValue(mockResponse({}))
    vi.stubGlobal('fetch', fetchMock)

    await apiFetch('/api/articles', { method: 'POST' })

    const [, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    const headers = new Headers(init.headers)
    expect(headers.get('X-XSRF-TOKEN')).toBe('abc123')
    expect(init.credentials).toBe('include')
  })

  it('does not attach X-XSRF-TOKEN on a GET request', async () => {
    document.cookie = 'XSRF-TOKEN=abc123'
    const fetchMock = vi.fn().mockResolvedValue(mockResponse({}))
    vi.stubGlobal('fetch', fetchMock)

    await apiFetch('/api/articles')

    const [, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    const headers = new Headers(init.headers)
    expect(headers.get('X-XSRF-TOKEN')).toBeNull()
  })

  it('omits the header when no XSRF-TOKEN cookie is set', async () => {
    const fetchMock = vi.fn().mockResolvedValue(mockResponse({}))
    vi.stubGlobal('fetch', fetchMock)

    await apiFetch('/api/articles', { method: 'DELETE' })

    const [, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    const headers = new Headers(init.headers)
    expect(headers.get('X-XSRF-TOKEN')).toBeNull()
  })
})

describe('request', () => {
  it('parses a JSON body on success', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(mockResponse({ text: () => Promise.resolve('{"id":1}') })),
    )

    const result = await request<{ id: number }>('/api/articles/1')
    expect(result).toEqual({ id: 1 })
  })

  it('returns undefined for an empty body', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(mockResponse({})))

    const result = await request<void>('/api/articles/1', { method: 'DELETE' })
    expect(result).toBeUndefined()
  })

  it('throws ApiError with the response status when not ok', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(mockResponse({ ok: false, status: 401 })))

    await expect(request('/api/articles')).rejects.toSatisfy(
      (err: unknown) => err instanceof ApiError && err.status === 401,
    )
  })
})
