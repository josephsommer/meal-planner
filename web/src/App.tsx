import { useEffect, useState } from 'react'

interface HelloResponse {
  message: string
  timestamp: string
}

export default function App() {
  const [data, setData] = useState<HelloResponse | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    const controller = new AbortController()
    fetch('/api/hello', { signal: controller.signal })
      .then((res) => {
        if (!res.ok) throw new Error(`HTTP ${res.status}`)
        return res.json() as Promise<HelloResponse>
      })
      .then(setData)
      .catch((err: unknown) => {
        if (err instanceof Error && err.name === 'AbortError') return
        setError(err instanceof Error ? err.message : 'Unknown error')
      })
    return () => controller.abort()
  }, [])

  if (error) return <p>Error: {error}</p>
  if (!data) return <p>Loading...</p>

  return (
    <div>
      <h1>{data.message}</h1>
      <p>{data.timestamp}</p>
    </div>
  )
}
