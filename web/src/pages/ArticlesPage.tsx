import { useState } from 'react'
import { useArticlesQuery, useCreateArticleMutation, useDeleteArticleMutation } from '../api/articles'
import type { Article } from '../api/articles'
import { useLogoutMutation } from '../api/auth'
import ArticleModal from '../components/ArticleModal'

function rowLabel(article: Article): string {
  const base = article.title ?? article.url
  return article.fetchStatus === 'FAILED' ? `${base} (failed)` : base
}

export default function ArticlesPage() {
  const [url, setUrl] = useState('')
  const [selectedId, setSelectedId] = useState<number | null>(null)
  const articlesQuery = useArticlesQuery()
  const createMutation = useCreateArticleMutation()
  const deleteMutation = useDeleteArticleMutation()
  const logoutMutation = useLogoutMutation()

  function handleSubmit(event: React.FormEvent) {
    event.preventDefault()
    createMutation.mutate(url, { onSuccess: () => setUrl('') })
  }

  const articles = articlesQuery.data ?? []
  const selectedArticle = articles.find((article) => article.id === selectedId) ?? null

  return (
    <div>
      <form onSubmit={handleSubmit}>
        <label htmlFor="article-url">Article URL</label>
        <input
          id="article-url"
          type="url"
          value={url}
          onChange={(event) => setUrl(event.target.value)}
          disabled={createMutation.isPending}
        />
        <button type="submit" disabled={createMutation.isPending}>
          Add
        </button>
      </form>
      {createMutation.isError && <p role="alert">Could not add that article</p>}

      <button type="button" onClick={() => logoutMutation.mutate()}>
        Log out
      </button>

      <table>
        <tbody>
          {articles.map((article) => (
            <tr key={article.id}>
              <td>{rowLabel(article)}</td>
              <td>{article.excerpt ?? ''}</td>
              <td>
                {article.fetchStatus === 'SUCCEEDED' && (
                  <button type="button" onClick={() => setSelectedId(article.id)}>
                    View
                  </button>
                )}
                <button type="button" onClick={() => deleteMutation.mutate(article.id)}>
                  Delete
                </button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>

      <ArticleModal article={selectedArticle} onClose={() => setSelectedId(null)} />
    </div>
  )
}
