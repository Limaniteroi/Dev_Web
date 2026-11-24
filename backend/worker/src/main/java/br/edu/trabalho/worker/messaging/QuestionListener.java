package br.edu.trabalho.worker.messaging;

import br.edu.trabalho.common.messaging.AnswerMessage;
import br.edu.trabalho.common.messaging.MessagingProperties;
import br.edu.trabalho.common.messaging.QuestionMessage;
import br.edu.trabalho.worker.tutor.QuestionProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * Consumidor de {@code perguntas.queue}.
 * <p>
 * Fluxo: (1) o broker entrega a pergunta ao primeiro Worker livre (prefetch=1);
 * (2) o JSON é convertido em {@link QuestionMessage}; (3-5) o {@link QuestionProcessor}
 * enriquece o prompt e chama a LLM com retry; (6) a resposta é publicada em
 * {@code respostas.exchange} com o mesmo correlationId.
 * <p>
 * Ack automático: a mensagem só sai da fila quando este método retorna sem exceção.
 * Se o Worker cair no meio do processamento, o RabbitMQ reentrega a pergunta.
 */
@Component
public class QuestionListener {

    private static final Logger log = LoggerFactory.getLogger(QuestionListener.class);

    private final QuestionProcessor processor;
    private final RabbitTemplate rabbitTemplate;
    private final MessagingProperties props;

    public QuestionListener(QuestionProcessor processor, RabbitTemplate rabbitTemplate, MessagingProperties props) {
        this.processor = processor;
        this.rabbitTemplate = rabbitTemplate;
        this.props = props;
    }

    @RabbitListener(queues = "${app.messaging.questions-queue}")
    public void onQuestion(QuestionMessage question) {
        if (question == null || isBlank(question.correlationId()) || isBlank(question.userId())
                || isBlank(question.question())) {
            // Sem correlationId/userId não há para quem responder: vai direto para a DLQ.
            throw new AmqpRejectAndDontRequeueException("Mensagem de pergunta inválida: " + question);
        }
        log.info("Pergunta {} recebida do usuário {}", question.correlationId(), question.userId());

        QuestionProcessor.Result result = processor.process(question);
        publish(result.answer());

        if (result.failed()) {
            // O usuário já foi avisado (status ERROR). A pergunta original vai para a DLQ
            // (perguntas.dlq) para auditoria/reprocessamento manual, sem loop de reentrega.
            throw new AmqpRejectAndDontRequeueException("LLM indisponível após retries", result.failure());
        }
    }

    private void publish(AnswerMessage answer) {
        // Routing key específica do usuário dono da pergunta (ex.: respostas.aluno)
        String routingKey = props.answersRoutingKeyFor(answer.userId());
        rabbitTemplate.convertAndSend(props.answersExchange(), routingKey, answer, message -> {
            message.getMessageProperties().setCorrelationId(answer.correlationId());
            return message;
        });
        log.info("Resposta {} publicada em {} com routing key {} (status {})",
                answer.correlationId(), props.answersExchange(), routingKey, answer.status());
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
