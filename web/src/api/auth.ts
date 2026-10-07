import { useMutation, useQueryClient } from '@tanstack/react-query'
import { apiFetch, ApiError, request } from './client'
import { articlesQueryKey } from './articles'

interface LoginResponse {
  username: string
}

async function login(username: string, password: string): Promise<LoginResponse> {
  const res = await apiFetch('/api/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({ username, password }),
  })
  if (!res.ok) {
    throw new ApiError(res.status, 'Invalid username or password')
  }
  return res.json() as Promise<LoginResponse>
}

async function logout(): Promise<void> {
  await request<void>('/api/auth/logout', { method: 'POST' })
}

export function useLoginMutation() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ username, password }: { username: string; password: string }) =>
      login(username, password),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: articlesQueryKey }),
  })
}

export function useLogoutMutation() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: logout,
    onSuccess: () => queryClient.resetQueries({ queryKey: articlesQueryKey }),
  })
}
