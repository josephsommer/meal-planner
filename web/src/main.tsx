import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import App from './App'

const rootElement = document.getElementById('root')
if (!rootElement) throw new Error('Root element not found')

// retry: false so the initial auth-check query (401 -> logged out) resolves
// immediately instead of retrying with backoff before App can render.
const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })

createRoot(rootElement).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <App />
    </QueryClientProvider>
  </StrictMode>,
)
