# Backend

Projeto Maven multi-módulo (Java 21, Spring Boot 3.5, Spring AI 1.1):

| Módulo | Papel |
|---|---|
| `common` | Contratos das mensagens (`QuestionMessage`, `AnswerMessage`), conversor Jackson e topologia RabbitMQ (exchanges, filas duráveis, DLQ), compartilhados pelos dois serviços |
| `gateway` | API HTTP (porta 8080): login/cadastro com JWT, publicação das perguntas, `@RabbitListener` das respostas e stream SSE por usuário |
| `worker` | Processo sem servidor web: consome `perguntas.queue`, enriquece o prompt, chama a LLM via Spring AI com retry e publica em `respostas.exchange` |

## Topologia RabbitMQ

```
perguntas.exchange --perguntas.nova-->   perguntas.queue --(rejeitada)--> perguntas.dlx --> perguntas.dlq
respostas.exchange --respostas.pronta--> respostas.queue
```

## Rodando

```bash
cp .env.example .env     # os dois serviços leem este arquivo automaticamente
./mvnw package           # Windows: mvnw.cmd package
java -jar worker/target/worker-0.0.1-SNAPSHOT.jar
java -jar gateway/target/gateway-0.0.1-SNAPSHOT.jar
```

O RabbitMQ (e, se quiser, o Ollama) sobe pelo `docker-compose.yml` da raiz. As instruções
completas estão no [README principal](../README.md).

## Provedor de LLM

Escolhido por `LLM_PROVIDER` no `.env`:

- `ollama`: modelo local gratuito (`OLLAMA_BASE_URL`, `OLLAMA_MODEL`, padrão `qwen3.5:4b`; `LLM_THINKING=false`). Baixe o modelo antes
  com `ollama pull` (ou use `OLLAMA_PULL_STRATEGY=when_missing`). O Worker sobe mesmo com o
  Ollama fora do ar; nesse caso as perguntas recebem a resposta de erro após as tentativas.
- `openai`: precisa de `OPENAI_API_KEY` (`OPENAI_MODEL`, padrão `gpt-4o-mini`).

Retry: `LLM_RETRY_MAX_ATTEMPTS` (3) e `LLM_RETRY_BACKOFF_MS` (2000). Teto de tokens por resposta:
`LLM_MAX_TOKENS` (2048).
