import 'katex/dist/katex.min.css'
import { useEffect, useRef } from 'react'
import Markdown from 'react-markdown'
import rehypeKatex from 'rehype-katex'
import remarkGfm from 'remark-gfm'
import remarkMath from 'remark-math'

// LLMs costumam escrever notação matemática em LaTeX ($O(n \log n)$); o KaTeX renderiza.
const remarkPlugins = [remarkGfm, remarkMath]
const rehypePlugins = [rehypeKatex]

// Alguns modelos usam \( \) e \[ \] em vez de $ e $$; converte fora dos trechos de código.
const CODE_SPANS = /(```[\s\S]*?```|`[^`\n]*`)/
function normalizeMath(text) {
  return text
    .split(CODE_SPANS)
    .map((part, i) => (i % 2 === 1
      ? part
      : part
        .replace(/\\\[([\s\S]+?)\\\]/g, (_, m) => `\n$$${m.trim()}$$\n`)
        .replace(/\\\(([\s\S]+?)\\\)/g, (_, m) => `$${m.trim()}$`)))
    .join('')
}

const timeFormat = new Intl.DateTimeFormat('pt-BR', { hour: '2-digit', minute: '2-digit' })

function formatTime(iso) {
  return iso ? timeFormat.format(new Date(iso)) : ''
}

function AnswerBody({ item }) {
  if (item.status === 'PENDING') {
    return (
      <p className="answer-pending" aria-live="polite">
        <span className="typing-dots" aria-hidden="true"><span /><span /><span /></span>
        O tutor está pensando na sua pergunta…
      </p>
    )
  }
  if (item.status === 'ERROR') {
    return <p className="answer-error" role="alert">{item.error}</p>
  }
  return (
    <div className="answer-markdown">
      <Markdown remarkPlugins={remarkPlugins} rehypePlugins={rehypePlugins}>{normalizeMath(item.answer)}</Markdown>
    </div>
  )
}

export default function Conversation({ items }) {
  const endRef = useRef(null)
  const lastKey = items.length ? `${items.at(-1).correlationId}:${items.at(-1).status}` : ''

  // Rola até o fim quando chega pergunta nova ou a última é respondida
  useEffect(() => {
    endRef.current?.scrollIntoView({ behavior: 'smooth', block: 'end' })
  }, [lastKey])

  return (
    <ol className="conversation">
      {items.map((item) => (
        <li key={item.correlationId} className={`qa-item status-${item.status.toLowerCase()}`}>
          <div className="qa-question">
            <p>{item.question}</p>
            <time dateTime={item.createdAt}>{formatTime(item.createdAt)}</time>
          </div>
          <div className="qa-answer">
            <AnswerBody item={item} />
            {item.answeredAt && <time dateTime={item.answeredAt}>{formatTime(item.answeredAt)}</time>}
          </div>
        </li>
      ))}
      <li ref={endRef} className="conversation-end" aria-hidden="true" />
    </ol>
  )
}
