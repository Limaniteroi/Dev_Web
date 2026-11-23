import { fetchEventSource } from '@microsoft/fetch-event-source'
import { useEffect, useRef, useState } from 'react'
import { api } from './api'

class FatalStreamError extends Error {}
class RetriableStreamError extends Error {}

const RETRY_MS = 3000

/**
 * Mantém aberto o stream SSE do usuário autenticado (GET /api/stream).
 *
 * Usa @microsoft/fetch-event-source em vez do EventSource nativo porque este último não
 * permite enviar o header Authorization com o JWT. O Gateway vincula o stream ao "sub" do
 * token, então só chegam aqui as respostas deste usuário.
 *
 * @param token     JWT (null => não conecta)
 * @param handlers  { onAnswer(view), onConnected(), onCleared(), onUnauthorized() }
 * @returns status da conexão: 'connecting' | 'online' | 'reconnecting'
 */
export function useAnswerStream(token, handlers) {
  const [status, setStatus] = useState('connecting')
  const handlersRef = useRef(handlers)

  useEffect(() => {
    handlersRef.current = handlers
  })

  useEffect(() => {
    if (!token) return undefined
    const controller = new AbortController()

    fetchEventSource(api.streamUrl, {
      headers: { Authorization: `Bearer ${token}`, Accept: 'text/event-stream' },
      signal: controller.signal,
      // Mantém a conexão mesmo com a aba em segundo plano (o padrão da lib é fechar)
      openWhenHidden: true,

      async onopen(response) {
        const type = response.headers.get('content-type') ?? ''
        if (response.ok && type.startsWith('text/event-stream')) {
          setStatus('online')
          return
        }
        if (response.status === 401 || response.status === 403) {
          throw new FatalStreamError('unauthorized')
        }
        throw new RetriableStreamError(`HTTP ${response.status}`)
      },

      onmessage(event) {
        if (event.event === 'conectado') {
          // A cada (re)conexão busca o histórico: recupera respostas que chegaram
          // enquanto o stream estava fechado.
          handlersRef.current.onConnected?.()
        } else if (event.event === 'resposta') {
          handlersRef.current.onAnswer?.(JSON.parse(event.data))
        } else if (event.event === 'historico-limpo') {
          // Conversa limpa em outra aba do mesmo usuário
          handlersRef.current.onCleared?.()
        }
      },

      onclose() {
        // Servidor encerrou (timeout do SseEmitter): reconecta
        throw new RetriableStreamError('closed')
      },

      onerror(error) {
        if (error instanceof FatalStreamError) {
          handlersRef.current.onUnauthorized?.()
          throw error // para de tentar
        }
        setStatus('reconnecting')
        return RETRY_MS
      },
    }).catch(() => {
      // abortado (logout/desmontagem) ou erro fatal já tratado
    })

    return () => controller.abort()
  }, [token])

  return status
}
