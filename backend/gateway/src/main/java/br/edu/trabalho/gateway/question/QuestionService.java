package br.edu.trabalho.gateway.question;

import br.edu.trabalho.common.messaging.QuestionMessage;
import br.edu.trabalho.gateway.config.GatewayProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.UUID;

@Service
public class QuestionService {

    private static final Logger log = LoggerFactory.getLogger(QuestionService.class);

    private final QuestionStore store;
    private final QuestionPublisher publisher;
    private final RateLimiter rateLimiter;
    private final GatewayProperties props;

    public QuestionService(QuestionStore store, QuestionPublisher publisher, RateLimiter rateLimiter,
                           GatewayProperties props) {
        this.store = store;
        this.publisher = publisher;
        this.rateLimiter = rateLimiter;
        this.props = props;
    }

    /**
     * Aceita a pergunta e a publica na fila. Retorna imediatamente (não espera a LLM):
     * a resposta chega depois, pelo stream SSE do usuário.
     */
    public QuestionView submit(String userId, String text) {
        String question = text.strip();
        if (question.length() > props.rateLimit().maxQuestionLength()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A pergunta deve ter no máximo " + props.rateLimit().maxQuestionLength() + " caracteres");
        }
        if (store.countPending(userId) >= props.rateLimit().maxPending()) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Aguarde as perguntas em andamento serem respondidas antes de enviar outra");
        }
        if (!rateLimiter.tryAcquire(userId)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Limite de " + props.rateLimit().questionsPerMinute() + " perguntas por minuto atingido");
        }

        String correlationId = UUID.randomUUID().toString();
        Instant now = Instant.now();
        QuestionView pending = QuestionView.pending(correlationId, userId, question, now);
        // Salva antes de publicar: a resposta pode chegar antes de este método retornar.
        store.save(pending);
        try {
            publisher.publish(new QuestionMessage(correlationId, userId, question, now));
        } catch (AmqpException e) {
            store.remove(correlationId);
            log.error("Falha ao publicar pergunta {} no RabbitMQ: {}", correlationId, e.toString());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Fila de perguntas indisponível. Tente novamente em instantes.");
        }
        log.info("Pergunta {} do usuário {} publicada", correlationId, userId);
        return pending;
    }
}
