import { useCallback, useRef, useState } from 'react'
import './App.css'
import { api, clearSession, loadSession, saveSession } from './api'
import Conversation from './Conversation'
import LoginPage from './LoginPage'
import { useAnswerStream } from './useAnswerStream'

const welcomeMessages = [
  'Nenhuma pergunta é uma pergunta boba',
  'Me ensine como se eu fosse um bebê',
  'Crescendo e estudando sempre',
  'De pouco em pouco o jardim floresce',
  'Vamos te ajudar da melhor forma possível',
]

const petImages = {
  cat: ['/cat-1.webp', '/cat-2.webp', '/cat-3.jpg'],
  dog: ['/dog-1.jpg', '/dog-2.jpg', '/dog-3.jpg'],
}

/** Sorteia uma foto diferente da última mostrada (para o clique seguinte sempre trazer novidade). */
function pickDifferent(images, previous) {
  const options = images.filter((src) => src !== previous)
  return options[Math.floor(Math.random() * options.length)]
}

function randomWelcome() {
  return welcomeMessages[Math.floor(Math.random() * welcomeMessages.length)]
}

const connectionLabels = {
  connecting: 'Conectando…',
  online: 'Conectado',
  reconnecting: 'Reconectando…',
}

/** Insere ou atualiza uma pergunta pelo correlationId, mantendo ordem cronológica. */
function upsert(items, view) {
  const index = items.findIndex((i) => i.correlationId === view.correlationId)
  if (index === -1) {
    return [...items, view].sort((a, b) => new Date(a.createdAt) - new Date(b.createdAt))
  }
  // Nunca "regride" uma pergunta já respondida para PENDING (o POST pode chegar depois do SSE)
  if (view.status === 'PENDING' && items[index].status !== 'PENDING') return items
  const next = [...items]
  next[index] = { ...items[index], ...view }
  return next
}

function TutorPage({ session, onLogout }) {
  const [question, setQuestion] = useState('')
  const [items, setItems] = useState([])
  const [sending, setSending] = useState(false)
  const [formError, setFormError] = useState('')
  const [petChooserOpen, setPetChooserOpen] = useState(false)
  const [celebratingPet, setCelebratingPet] = useState(null) // { src, id } | null
  const lastPetRef = useRef(null)
  const [clearing, setClearing] = useState(false)
  const [welcomeMessage, setWelcomeMessage] = useState(randomWelcome)
  const textareaRef = useRef(null)

  const refreshHistory = useCallback(async () => {
    try {
      const history = await api.history(session.token)
      setItems((current) => history.reduce(upsert, current))
    } catch (err) {
      if (err.status === 401) onLogout()
    }
  }, [session.token, onLogout])

  function resetConversation() {
    setItems([])
    setFormError('')
    setWelcomeMessage(randomWelcome())
  }

  const connection = useAnswerStream(session.token, {
    onConnected: refreshHistory,
    onAnswer: (view) => setItems((current) => upsert(current, view)),
    onCleared: resetConversation,
    onUnauthorized: onLogout,
  })

  /** Apaga a conversa no servidor (inclusive perguntas em andamento) e recomeça do zero. */
  async function handleClear() {
    if (clearing || !window.confirm('Apagar toda a conversa e começar do zero?')) return
    setClearing(true)
    try {
      await api.clearHistory(session.token)
      resetConversation()
      textareaRef.current?.focus()
    } catch (err) {
      if (err.status === 401) onLogout()
      else setFormError(err.message)
    } finally {
      setClearing(false)
    }
  }

  function resizeTextarea(textarea) {
    textarea.style.height = 'auto'
    textarea.style.height = `${Math.min(textarea.scrollHeight, 260)}px`
  }

  function handleQuestionChange(event) {
    setQuestion(event.target.value)
    setFormError('')
    resizeTextarea(event.target)
  }

  async function handleSubmit(event) {
    event?.preventDefault()
    const text = question.trim()
    if (!text || sending) return
    setSending(true)
    setFormError('')
    try {
      const pending = await api.ask(session.token, text)
      setItems((current) => upsert(current, pending))
      setQuestion('')
      if (textareaRef.current) {
        textareaRef.current.style.height = 'auto'
      }
    } catch (err) {
      if (err.status === 401) onLogout()
      else setFormError(err.message)
    } finally {
      setSending(false)
    }
  }

  function handleKeyDown(event) {
    // Enter envia; Shift+Enter quebra linha
    if (event.key === 'Enter' && !event.shiftKey && !event.nativeEvent.isComposing) {
      event.preventDefault()
      handleSubmit()
    }
  }

  function choosePet(pet) {
    setPetChooserOpen(false)
    const images = petImages[pet]
    lastPetRef.current = pickDifferent(images, lastPetRef.current)
    // id novo a cada escolha: o React recria o <img> e a animação sempre recomeça,
    // mesmo se a foto anterior ainda estiver na tela
    setCelebratingPet({ src: lastPetRef.current, id: Date.now() })
  }

  const hasItems = items.length > 0

  return (
    <main className={`home-page ${hasItems ? 'has-answer' : ''}`}>
      <header className="session-bar">
        <span className={`connection connection-${connection}`} title="Conexão em tempo real (SSE)">
          <span className="connection-dot" aria-hidden="true" />
          {connectionLabels[connection]}
        </span>
        {hasItems && (
          <button
            type="button"
            className="clear-button"
            onClick={handleClear}
            disabled={clearing}
            title="Apagar o histórico e começar uma conversa nova"
          >
            <svg viewBox="0 0 24 24" width="14" height="14" aria-hidden="true">
              <path fill="currentColor" d="M9 3h6l1 2h4v2H4V5h4l1-2Zm-3 6h12l-1 12H7L6 9Zm4 2v8h2v-8h-2Zm4 0v8h2v-8h-2Z" />
            </svg>
            {clearing ? 'Limpando…' : 'Limpar conversa'}
          </button>
        )}
        <span className="session-user">{session.username}</span>
        <button type="button" className="logout-button" onClick={onLogout}>Sair</button>
      </header>

      <section className="question-area">
        <h1>{welcomeMessage}</h1>
        <form className={`question-bar ${question.length > 76 ? 'is-expanded' : ''}`} onSubmit={handleSubmit}>
          <button className="smiling-button" type="button" onClick={() => setPetChooserOpen((open) => !open)} aria-label="Escolher cachorro ou gato">
            <img className="smiling-icon" src="/smiling.png" alt="Escolher animal" />
          </button>
          <textarea
            ref={textareaRef}
            value={question}
            onChange={handleQuestionChange}
            onKeyDown={handleKeyDown}
            placeholder="Envie a sua pergunta para gente"
            aria-label="Digite sua pergunta"
            maxLength={2000}
            rows="1"
          />
          <button type="submit" aria-label="Enviar pergunta" disabled={sending || !question.trim()}>
            <img src="/paper-plane-message.png" alt="Enviar" />
          </button>
        </form>
        {formError && <p className="form-error question-error" role="alert">{formError}</p>}
      </section>

      {petChooserOpen && (
        <div className="pet-overlay" role="dialog" aria-label="Escolha um animal">
          <div className="pet-chooser">
            <button className="pet-close" type="button" onClick={() => setPetChooserOpen(false)} aria-label="Fechar escolha de animal">×</button>
            <h2>Momento de Descontração</h2>
            <p>Escolha uma companhia</p>
            <div className="pet-options">
              <button type="button" onClick={() => choosePet('cat')}>🐱 Gato</button>
              <button type="button" onClick={() => choosePet('dog')}>🐶 Cachorro</button>
            </div>
          </div>
        </div>
      )}
      {celebratingPet && (
        <div className="pet-celebration" aria-label="Animal escolhido">
          <img
            key={celebratingPet.id}
            src={celebratingPet.src}
            alt="Animal escolhido"
            /* sai da tela exatamente quando a animação termina */
            onAnimationEnd={() => setCelebratingPet((current) => (current?.id === celebratingPet.id ? null : current))}
          />
        </div>
      )}
      {hasItems && (
        <section className="answer-area" aria-label="Área da resposta da inteligência artificial">
          <Conversation items={items} />
        </section>
      )}
    </main>
  )
}

function App() {
  const [session, setSession] = useState(loadSession)

  const handleLogin = useCallback((newSession) => {
    saveSession(newSession)
    setSession(newSession)
  }, [])

  const handleLogout = useCallback(() => {
    clearSession()
    setSession(null)
  }, [])

  if (!session) return <LoginPage onLogin={handleLogin} />
  // key: troca de usuário recria a página (stream e estado limpos)
  return <TutorPage key={session.username} session={session} onLogout={handleLogout} />
}

export default App
