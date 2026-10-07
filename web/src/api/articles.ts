import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { request, type ApiError } from './client'

export type FetchStatus = 'PENDING' | 'SUCCEEDED' | 'FAILED'

export interface Article {
  id: number
  url: string
  createdAt: string
  fetchStatus: FetchStatus
  title: string | null
  excerpt: string | null
  content: string | null
}

export const articlesQueryKey = ['articles'] as const

export const fetchArticles = () => request<Article[]>('/api/articles')

export const createArticle = (url: string) =>
  request<Article>('/api/articles', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ url }),
  })

export const deleteArticle = (id: number) =>
  request<void>(`/api/articles/${id}`, { method: 'DELETE' })

export function useArticlesQuery() {
  return useQuery<Article[], ApiError>({
    queryKey: articlesQueryKey,
    queryFn: fetchArticles,
    refetchInterval: (query) =>
      query.state.data?.some((article) => article.fetchStatus === 'PENDING') ? 3000 : false,
  })
}

export function useCreateArticleMutation() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: createArticle,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: articlesQueryKey }),
  })
}

export function useDeleteArticleMutation() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: deleteArticle,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: articlesQueryKey }),
  })
}
