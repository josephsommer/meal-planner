import { screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import App from './App'
import { renderWithQueryClient } from './test-utils'

function jsonResponse(body: unknown, init: { ok?: boolean; status?: number } = {}) {
  return {
    ok: init.ok ?? true,
    status: init.status ?? 200,
    text: () => Promise.resolve(JSON.stringify(body)),
  }
}

describe('App', () => {
  it('shows the login view when the initial articles check is unauthenticated', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(null, { ok: false, status: 401 })))
    renderWithQueryClient(<App />)

    expect(await screen.findByLabelText('Username')).toBeInTheDocument()
  })

  it('shows the articles view when the initial articles check succeeds', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse([])))
    renderWithQueryClient(<App />)

    expect(await screen.findByLabelText('Article URL')).toBeInTheDocument()
  })

  it('shows a generic error for a non-401 failure', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse(null, { ok: false, status: 500 })))
    renderWithQueryClient(<App />)

    expect(await screen.findByRole('alert')).toHaveTextContent('Something went wrong')
  })
})
