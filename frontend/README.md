# Frontend

React 19 + Vite. Telas de login/cadastro e de perguntas. As respostas chegam em tempo real por SSE.

- [src/api.js](src/api.js): chamadas ao Gateway e sessão (JWT no `localStorage`).
- [src/useAnswerStream.js](src/useAnswerStream.js): stream SSE com `@microsoft/fetch-event-source`
  (envia `Authorization: Bearer`), reconexão automática e recarga do histórico a cada reconexão.
- [src/Conversation.jsx](src/Conversation.jsx): lista de perguntas e respostas (Markdown com `react-markdown`).

```bash
npm install
npm run dev     # http://localhost:5173 — proxy de /api para http://127.0.0.1:8080
npm run lint
npm run build
```

Para apontar o proxy para outro Gateway: `VITE_PROXY_TARGET=http://host:porta npm run dev`.
Em produção (Docker), o Nginx serve o build e faz o proxy de `/api` ([nginx.conf](nginx.conf)).
