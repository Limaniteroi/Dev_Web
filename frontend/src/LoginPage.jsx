import { useState } from 'react'
import { api } from './api'

export default function LoginPage({ onLogin }) {
  const [mode, setMode] = useState('login')
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)

  const isLogin = mode === 'login'

  async function handleSubmit(event) {
    event.preventDefault()
    setError('')
    setLoading(true)
    try {
      const session = isLogin
        ? await api.login(username.trim(), password)
        : await api.register(username.trim(), password)
      onLogin(session)
    } catch (err) {
      setError(err.status === 401 ? 'Usuário ou senha inválidos.' : err.message)
    } finally {
      setLoading(false)
    }
  }

  function switchMode() {
    setMode(isLogin ? 'register' : 'login')
    setError('')
  }

  return (
    <main className="login-page">
      <form className="login-card" onSubmit={handleSubmit}>
        <h1>Tutoria com IA</h1>
        <p className="login-subtitle">
          {isLogin ? 'Entre para enviar suas perguntas' : 'Crie sua conta para começar'}
        </p>

        <label>
          Usuário
          <input
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            autoComplete="username"
            autoFocus
            required
            minLength={isLogin ? undefined : 3}
            maxLength={32}
          />
        </label>
        <label>
          Senha
          <input
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            autoComplete={isLogin ? 'current-password' : 'new-password'}
            required
            minLength={isLogin ? undefined : 6}
          />
        </label>

        {error && <p className="form-error" role="alert">{error}</p>}

        <button className="primary-button" type="submit" disabled={loading}>
          {loading ? 'Aguarde…' : isLogin ? 'Entrar' : 'Criar conta'}
        </button>
        <button className="link-button" type="button" onClick={switchMode}>
          {isLogin ? 'Não tem conta? Cadastre-se' : 'Já tem conta? Entrar'}
        </button>
        {isLogin && <p className="login-hint">Demonstração: <strong>aluno</strong> / <strong>senha123</strong></p>}
      </form>
    </main>
  )
}
