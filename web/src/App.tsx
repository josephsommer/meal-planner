import { useEffect, useState } from 'react'

interface HelloResponse {
  message: string
  timestamp: string
}

export default function App() {
  const [data, setData] = useState<HelloResponse | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    fetch('/api/hello')
      .then((res) => {
        if (!res.ok) throw new Error(`HTTP ${res.status}`)
        return res.json() as Promise<HelloResponse>
      })
      .then(setData)
      .catch((err: unknown) => {
        setError(err instanceof Error ? err.message : 'Unknown error')
      })
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
