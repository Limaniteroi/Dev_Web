package br.edu.trabalho.gateway.question;

import br.edu.trabalho.common.messaging.MessagingProperties;
import br.edu.trabalho.common.messaging.QuestionMessage;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * Publica a pergunta em {@code perguntas.exchange}. Usa publisher confirms: só retorna depois
 * que o broker confirma ter recebido (e persistido) a mensagem; caso contrário lança exceção
 * e o usuário recebe 503 em vez de achar que a pergunta foi aceita.
 */
@Component
public class QuestionPublisher {

    private static final long CONFIRM_TIMEOUT_MS = 5_000;

    private final RabbitTemplate rabbitTemplate;
    private final MessagingProperties props;

    public QuestionPublisher(RabbitTemplate rabbitTemplate, MessagingProperties props) {
        this.rabbitTemplate = rabbitTemplate;
        this.props = props;
    }

    public void publish(QuestionMessage question) {
        rabbitTemplate.invoke(ops -> {
            ops.convertAndSend(props.questionsExchange(), props.questionsRoutingKey(), question, message -> {
                message.getMessageProperties().setCorrelationId(question.correlationId());
                message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                return message;
            });
            ops.waitForConfirmsOrDie(CONFIRM_TIMEOUT_MS);
            return null;
        });
    }
}
