package br.edu.trabalho.gateway.messaging;

import br.edu.trabalho.common.messaging.AnswerMessage;
import br.edu.trabalho.gateway.question.QuestionStore;
import br.edu.trabalho.gateway.question.QuestionView;
import br.edu.trabalho.gateway.sse.SseHub;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Ponte AMQP -> SSE: consome {@code respostas.queue}, atualiza o histórico e envia a resposta
 * pelo(s) SseEmitter(s) do dono da pergunta.
 */
@Component
public class AnswerListener {

    private static final Logger log = LoggerFactory.getLogger(AnswerListener.class);
    static final String EVENT_NAME = "resposta";

    private final QuestionStore store;
    private final SseHub hub;

    public AnswerListener(QuestionStore store, SseHub hub) {
        this.store = store;
        this.hub = hub;
    }

    @RabbitListener(queues = "${app.messaging.answers-queue}")
    public void onAnswer(AnswerMessage answer) {
        if (answer == null || answer.correlationId() == null || answer.userId() == null) {
            throw new AmqpRejectAndDontRequeueException("Resposta inválida: " + answer);
        }
        Optional<QuestionView> applied = store.applyAnswer(answer);
        if (applied.isEmpty()) {
            log.info("Resposta {} descartada: o usuário {} limpou a conversa antes de ela chegar",
                    answer.correlationId(), answer.userId());
            return;
        }
        QuestionView view = applied.get();
        int delivered = hub.send(answer.userId(), EVENT_NAME, view);
        log.info("Resposta {} ({}) entregue a {} conexão(ões) SSE de {}",
                answer.correlationId(), view.status(), delivered, answer.userId());
        // Se o usuário estava offline (delivered == 0) a resposta fica no histórico e o
        // front-end a busca em GET /api/perguntas ao reconectar.
    }
}
