import { fireEvent, screen, waitFor } from '@testing-library/react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import LoginPage from './LoginPage'
import { renderWithQueryClient } from '../test-utils'

function jsonResponse(body: unknown, init: { ok?: boolean; status?: number } = {}) {
  return {
    ok: init.ok ?? true,
    status: init.status ?? 200,
    json: () => Promise.resolve(body),
    text: () => Promise.resolve(JSON.stringify(body)),
  }
}

describe('LoginPage', () => {
  let fetchMock: ReturnType<typeof vi.fn>

  beforeEach(() => {
    fetchMock = vi.fn((url: string) => {
      if (url.includes('/api/auth/login')) {
        return Promise.resolve(jsonResponse({ username: 'admin' }))
      }
      // The login mutation's onSuccess invalidates the articles query, which
      // triggers a background refetch — give it something to resolve.
      return Promise.resolve(jsonResponse([]))
    })
    vi.stubGlobal('fetch', fetchMock)
  })

  it('submits credentials as form-urlencoded to /api/auth/login', async () => {
    renderWithQueryClient(<LoginPage />)

    fireEvent.change(screen.getByLabelText('Username'), { target: { value: 'admin' } })
    fireEvent.change(screen.getByLabelText('Password'), { target: { value: 'secret' } })
    fireEvent.click(screen.getByRole('button', { name: 'Log in' }))

    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith('/api/auth/login', expect.anything()))

    const [, init] = fetchMock.mock.calls.find(([url]) => url === '/api/auth/login') as [
      string,
      RequestInit,
    ]
    expect(init.method).toBe('POST')
    expect(new Headers(init.headers).get('Content-Type')).toBe('application/x-www-form-urlencoded')
    expect((init.body as URLSearchParams).toString()).toBe('username=admin&password=secret')
  })

  it('shows an error on invalid credentials', async () => {
    fetchMock.mockImplementation((url: string) => {
      if (url.includes('/api/auth/login')) {
        return Promise.resolve(jsonResponse(null, { ok: false, status: 401 }))
      }
      return Promise.resolve(jsonResponse([]))
    })

    renderWithQueryClient(<LoginPage />)

    fireEvent.change(screen.getByLabelText('Username'), { target: { value: 'admin' } })
    fireEvent.change(screen.getByLabelText('Password'), { target: { value: 'wrong' } })
    fireEvent.click(screen.getByRole('button', { name: 'Log in' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Invalid username or password')
  })
})
