import { fireEvent, screen, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import ArticlesPage from './ArticlesPage'
import { renderWithQueryClient } from '../test-utils'
import type { Article } from '../api/articles'

function jsonResponse(body: unknown, init: { ok?: boolean; status?: number } = {}) {
  return {
    ok: init.ok ?? true,
    status: init.status ?? 200,
    text: () => Promise.resolve(JSON.stringify(body)),
  }
}

const succeeded: Article = {
  id: 1,
  url: 'https://example.com/a',
  createdAt: '2026-01-01T00:00:00Z',
  fetchStatus: 'SUCCEEDED',
  title: 'Example A',
  excerpt: 'An example article',
  content: 'full text',
}

const failed: Article = {
  id: 2,
  url: 'https://example.com/b',
  createdAt: '2026-01-01T00:00:00Z',
  fetchStatus: 'FAILED',
  title: null,
  excerpt: null,
  content: null,
}

const pending: Article = {
  id: 3,
  url: 'https://example.com/c',
  createdAt: '2026-01-01T00:00:00Z',
  fetchStatus: 'PENDING',
  title: null,
  excerpt: null,
  content: null,
}

describe('ArticlesPage', () => {
  afterEach(() => {
    document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT'
  })

  it('renders title/excerpt rows with no table header, marking failed articles', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse([succeeded, failed])))
    renderWithQueryClient(<ArticlesPage />)

    expect(await screen.findByText('Example A')).toBeInTheDocument()
    expect(screen.getByText('An example article')).toBeInTheDocument()
    expect(screen.getByText('https://example.com/b (failed)')).toBeInTheDocument()
    expect(screen.queryAllByRole('columnheader')).toHaveLength(0)
  })

  it('falls back to the raw url when a pending article has no title yet', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(jsonResponse([pending])))
    renderWithQueryClient(<ArticlesPage />)

    expect(await screen.findByText('https://example.com/c')).toBeInTheDocument()
  })

  describe('adding an article', () => {
    let fetchMock: ReturnType<typeof vi.fn>

    beforeEach(() => {
      document.cookie = 'XSRF-TOKEN=test-token'
      fetchMock = vi.fn((_url: string, init?: RequestInit) => {
        if (init?.method === 'POST') return Promise.resolve(jsonResponse(succeeded))
        return Promise.resolve(jsonResponse([]))
      })
      vi.stubGlobal('fetch', fetchMock)
    })

    it('posts the url with the CSRF header and clears the input', async () => {
      renderWithQueryClient(<ArticlesPage />)
      await waitFor(() => expect(fetchMock).toHaveBeenCalled())

      fireEvent.change(screen.getByLabelText('Article URL'), {
        target: { value: 'https://example.com/new' },
      })
      fireEvent.click(screen.getByRole('button', { name: 'Add' }))

      await waitFor(() =>
        expect(fetchMock).toHaveBeenCalledWith('/api/articles', expect.objectContaining({ method: 'POST' })),
      )
      const call = fetchMock.mock.calls.find((c) => (c[1] as RequestInit | undefined)?.method === 'POST')
      const init = call?.[1] as RequestInit
      expect(JSON.parse(init.body as string)).toEqual({ url: 'https://example.com/new' })
      expect(new Headers(init.headers).get('X-XSRF-TOKEN')).toBe('test-token')
    })
  })

  describe('deleting an article', () => {
    let fetchMock: ReturnType<typeof vi.fn>

    beforeEach(() => {
      document.cookie = 'XSRF-TOKEN=test-token'
      let deleted = false
      fetchMock = vi.fn((_url: string, init?: RequestInit) => {
        if (init?.method === 'DELETE') {
          deleted = true
          return Promise.resolve(jsonResponse(undefined))
        }
        return Promise.resolve(jsonResponse(deleted ? [] : [succeeded]))
      })
      vi.stubGlobal('fetch', fetchMock)
    })

    it('sends a DELETE with the CSRF header and removes the row', async () => {
      renderWithQueryClient(<ArticlesPage />)
      expect(await screen.findByText('Example A')).toBeInTheDocument()

      fireEvent.click(screen.getByRole('button', { name: 'Delete' }))

      await waitFor(() =>
        expect(fetchMock).toHaveBeenCalledWith('/api/articles/1', expect.objectContaining({ method: 'DELETE' })),
      )
      const call = fetchMock.mock.calls.find(
        (c) => c[0] === '/api/articles/1' && (c[1] as RequestInit | undefined)?.method === 'DELETE',
      )
      const init = call?.[1] as RequestInit
      expect(new Headers(init.headers).get('X-XSRF-TOKEN')).toBe('test-token')

      await waitFor(() => expect(screen.queryByText('Example A')).not.toBeInTheDocument())
    })
  })
})
