# Backend — perguntas e respostas em tempo real

Estrutura de configuração para dois serviços Spring Boot, sem código-fonte Java nesta etapa:

- `gateway`: API HTTP, publicação de perguntas no RabbitMQ e camada WebSocket/STOMP.
- `worker`: processo sem Tomcat (`web-application-type: none`), consumo assíncrono, Spring AI, retry e publicação das respostas.
- `infra/rabbitmq`: RabbitMQ durável com plugin STOMP habilitado.

## Topologia

`perguntas.exchange` → `perguntas.queue` → Worker → `respostas.exchange` → `respostas.queue`.

O frontend deve assinar o tópico STOMP correspondente à sessão/correlation id. A configuração de filas, exchanges, bindings e serialização Jackson deverá ser implementada nas classes de configuração dos serviços quando o código for iniciado.

## Execução da infraestrutura

Na pasta `backend/infra/rabbitmq`, execute `docker compose up -d`. O painel fica em `http://localhost:15672` com usuário `app` e senha `app`; o STOMP TCP fica na porta `61613`.

## Observação

Os nomes de filas, exchanges, routing keys, portas, retry e credenciais estão centralizados nos `application.yml` e podem ser sobrescritos por variáveis de ambiente.
