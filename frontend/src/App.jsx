import { useState } from 'react'
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
  const [welcomeMessage] = useState(
    () => welcomeMessages[Math.floor(Math.random() * welcomeMessages.length)],
  )
  function handleSubmit(event) { event.preventDefault(); if (question.trim()) setQuestion('') }
  return (
    <main className="home-page">
      <section className="question-area">
        <h1>{welcomeMessage}</h1>
        <form className="question-bar" onSubmit={handleSubmit}>
          <img className="smiling-icon" src="/smiling.png" alt="" />
          <input value={question} onChange={(event) => setQuestion(event.target.value)} placeholder="Envie a sua pergunta para gente" aria-label="Digite sua pergunta" />
          <button type="submit" aria-label="Enviar pergunta"><img src="/paper-plane-message.png" alt="Enviar" /></button>
        </form>
      </section>
    </main>
  )
}
export default App
