import { useArticlesQuery } from './api/articles'
import ArticlesPage from './pages/ArticlesPage'
import LoginPage from './pages/LoginPage'

export default function App() {
  const { error, isPending } = useArticlesQuery()

  if (isPending) return <p>Loading...</p>
  if (error) {
    if (error.status === 401) return <LoginPage />
    return <p role="alert">Something went wrong. Please reload the page.</p>
  }
  return <ArticlesPage />
}
