import { render, screen } from '@testing-library/react'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import App from './App'

describe('App', () => {
  beforeEach(() => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue({
        ok: true,
        json: () =>
          Promise.resolve({
            message: 'Hello from Compendium API',
            timestamp: '2026-05-27T00:00:00Z',
          }),
      }),
    )
  })

  it('displays the hello message from the API', async () => {
    render(<App />)
    const message = await screen.findByText('Hello from Compendium API')
    expect(message).toBeInTheDocument()
  })
})
