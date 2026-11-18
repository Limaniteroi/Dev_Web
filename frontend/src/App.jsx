import { useEffect, useRef, useState } from 'react'
import './App.css'

const welcomeMessages = [
  'Nenhuma pergunta é uma pergunta boba',
  'Me ensine como se eu fosse um bebê',
  'Crescendo e estudando sempre',
  'De pouco em pouco o jardim floresce',
  'Vamos te ajudar da melhor forma possível',
]

function App() {
  const [question, setQuestion] = useState('')
  const [submitted, setSubmitted] = useState(false)
  const [petChooserOpen, setPetChooserOpen] = useState(false)
  const [celebratingPet, setCelebratingPet] = useState(null)
  const [welcomeMessage] = useState(() => welcomeMessages[Math.floor(Math.random() * welcomeMessages.length)])
  const textareaRef = useRef(null)

  function handleQuestionChange(event) {
    const textarea = event.target
    setQuestion(textarea.value)
    textarea.style.height = 'auto'
    textarea.style.height = `${Math.min(textarea.scrollHeight, 260)}px`
  }

  function handleSubmit(event) {
    event.preventDefault()
    if (question.trim()) setSubmitted(true)
  }

  function choosePet(pet) {
    setPetChooserOpen(false)
    const images = pet === 'cat'
      ? ['/cat-1.webp', '/cat-2.webp', '/cat-3.jpg']
      : ['/dog-1.jpg', '/dog-2.jpg', '/dog-3.jpg']
    setCelebratingPet(images[Math.floor(Math.random() * images.length)])
  }

  useEffect(() => {
    if (!celebratingPet) return undefined
    const timer = window.setTimeout(() => setCelebratingPet(null), 3000)
    return () => window.clearTimeout(timer)
  }, [celebratingPet])


  return (
    <main className={`home-page ${submitted ? 'has-answer' : ''}`}>
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
            placeholder="Envie a sua pergunta para gente"
            aria-label="Digite sua pergunta"
            rows="1"
          />
          <button type="submit" aria-label="Enviar pergunta"><img src="/paper-plane-message.png" alt="Enviar" /></button>
        </form>
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
          <img src={celebratingPet} alt="Animal escolhido" />
        </div>
      )}
      {submitted && <section className="answer-area" aria-label="Área da resposta da inteligência artificial" />}
    </main>
  )
}

export default App
