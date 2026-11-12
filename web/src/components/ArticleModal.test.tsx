import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import ArticleModal from './ArticleModal'
import type { Article } from '../api/articles'

const article: Article = {
  id: 1,
  url: 'https://example.com/a',
  createdAt: '2026-01-01T00:00:00Z',
  fetchStatus: 'SUCCEEDED',
  title: 'Example A',
  excerpt: 'An example article',
  content: 'full text of the article',
}

describe('ArticleModal', () => {
  it('shows the title and full content in an open dialog', () => {
    render(<ArticleModal article={article} onClose={vi.fn()} />)

    expect(screen.getByRole('dialog', { name: 'Example A' })).toBeInTheDocument()
    expect(screen.getByText('full text of the article')).toBeInTheDocument()
  })

  it('renders nothing visible when there is no article', () => {
    render(<ArticleModal article={null} onClose={vi.fn()} />)

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
  })

  it('calls onClose from the close button', () => {
    const onClose = vi.fn()
    render(<ArticleModal article={article} onClose={onClose} />)

    fireEvent.click(screen.getByRole('button', { name: 'Close' }))

    expect(onClose).toHaveBeenCalled()
  })

  it('stays open when re-rendered with a new object for the same article', () => {
    const { rerender } = render(<ArticleModal article={article} onClose={vi.fn()} />)

    rerender(<ArticleModal article={{ ...article }} onClose={vi.fn()} />)

    expect(screen.getByRole('dialog')).toBeInTheDocument()
  })
})
