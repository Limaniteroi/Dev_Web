// Cliente HTTP do Gateway. Em desenvolvimento o Vite faz proxy de /api para o Spring Boot.
const API_BASE = import.meta.env.VITE_API_BASE ?? '/api'
const SESSION_KEY = 'tutoria.session'

export class ApiError extends Error {
  constructor(status, message) {
    super(message)
    this.status = status
  }
}

export function loadSession() {
  try {
    const session = JSON.parse(localStorage.getItem(SESSION_KEY))
    if (session?.token && new Date(session.expiresAt) > new Date()) return session
  } catch {
    // sessão corrompida ou storage indisponível
  }
  return null
}

export function saveSession(session) {
  try {
    localStorage.setItem(SESSION_KEY, JSON.stringify(session))
  } catch {
    // modo privado: segue só em memória
  }
}

export function clearSession() {
  try {
    localStorage.removeItem(SESSION_KEY)
  } catch {
    // ignora
  }
}

async function request(path, { token, method = 'GET', body } = {}) {
  const headers = { Accept: 'application/json' }
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  if (token) headers.Authorization = `Bearer ${token}`

  let response
  try {
    response = await fetch(`${API_BASE}${path}`, {
      method,
      headers,
      body: body !== undefined ? JSON.stringify(body) : undefined,
    })
  } catch {
    throw new ApiError(0, 'Não foi possível falar com o servidor. Verifique se o Gateway está rodando.')
  }

  const data = await response.json().catch(() => null)
  if (!response.ok) {
    const message = data?.detail || data?.error || defaultMessage(response.status)
    throw new ApiError(response.status, message)
  }
  return data
}

function defaultMessage(status) {
  if (status === 401) return 'Sessão expirada. Entre novamente.'
  if (status === 403) return 'Acesso negado pelo servidor (verifique a origem/CORS do Gateway).'
  if (status === 429) return 'Muitas perguntas seguidas. Aguarde um pouco.'
  if (status >= 500) return 'O servidor está indisponível no momento.'
  return 'Não foi possível concluir a operação.'
}

export const api = {
  streamUrl: `${API_BASE}/stream`,
  login: (username, password) => request('/auth/login', { method: 'POST', body: { username, password } }),
  register: (username, password) => request('/auth/register', { method: 'POST', body: { username, password } }),
  ask: (token, question) => request('/perguntas', { token, method: 'POST', body: { question } }),
  history: (token) => request('/perguntas', { token }),
  clearHistory: (token) => request('/perguntas', { token, method: 'DELETE' }),
}
