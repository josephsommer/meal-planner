import { useState } from 'react'
import { useLoginMutation } from '../api/auth'

export default function LoginPage() {
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const loginMutation = useLoginMutation()

  function handleSubmit(event: React.FormEvent) {
    event.preventDefault()
    loginMutation.mutate({ username, password })
  }

  return (
    <form onSubmit={handleSubmit}>
      <div>
        <label htmlFor="username">Username</label>
        <input
          id="username"
          value={username}
          onChange={(event) => setUsername(event.target.value)}
          autoComplete="username"
        />
      </div>
      <div>
        <label htmlFor="password">Password</label>
        <input
          id="password"
          type="password"
          value={password}
          onChange={(event) => setPassword(event.target.value)}
          autoComplete="current-password"
        />
      </div>
      <button type="submit" disabled={loginMutation.isPending}>
        Log in
      </button>
      {loginMutation.isError && <p role="alert">Invalid username or password</p>}
    </form>
  )
}
