package br.edu.trabalho.common.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Topologia RabbitMQ compartilhada. Gateway e Worker declaram a mesma estrutura
 * (operação idempotente), então qualquer um dos dois pode subir primeiro.
 *
 * <pre>
 * perguntas.exchange --perguntas.nova--> perguntas.queue --(rejeitada)--> perguntas.dlx --> perguntas.dlq
 * respostas.exchange (topic) --respostas.&lt;userId&gt;--> [binding respostas.#] --> respostas.queue
 * </pre>
 *
 * A resposta é publicada com a routing key do usuário (ex.: {@code respostas.aluno}); o binding
 * {@code respostas.#} da exchange topic encaminha a de qualquer usuário para respostas.queue.
 *
 * Exchanges e filas são duráveis e as mensagens são publicadas como PERSISTENT
 * (padrão do RabbitTemplate), então sobrevivem a um restart do broker.
 */
@Configuration
@EnableConfigurationProperties(MessagingProperties.class)
public class RabbitTopologyConfig {

    /**
     * Converte objetos Java <-> JSON nas duas pontas. Usa o ObjectMapper do Spring Boot
     * (com suporte a java.time). O Spring Boot conecta este bean automaticamente ao
     * RabbitTemplate e aos containers de @RabbitListener.
     */
    @Bean
    public MessageConverter jacksonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    @Bean
    public Declarables messagingTopology(MessagingProperties props) {
        DirectExchange questionsExchange = ExchangeBuilder.directExchange(props.questionsExchange())
                .durable(true).build();
        DirectExchange questionsDlx = ExchangeBuilder.directExchange(props.questionsDeadLetterExchange())
                .durable(true).build();
        TopicExchange answersExchange = ExchangeBuilder.topicExchange(props.answersExchange())
                .durable(true).build();

        Queue questionsQueue = QueueBuilder.durable(props.questionsQueue())
                .deadLetterExchange(props.questionsDeadLetterExchange())
                .deadLetterRoutingKey(props.questionsDeadLetterQueue())
                .build();
        Queue questionsDlq = QueueBuilder.durable(props.questionsDeadLetterQueue()).build();
        Queue answersQueue = QueueBuilder.durable(props.answersQueue()).build();

        Binding questionsBinding = BindingBuilder.bind(questionsQueue).to(questionsExchange)
                .with(props.questionsRoutingKey());
        Binding dlqBinding = BindingBuilder.bind(questionsDlq).to(questionsDlx)
                .with(props.questionsDeadLetterQueue());
        Binding answersBinding = BindingBuilder.bind(answersQueue).to(answersExchange)
                .with(props.answersBindingPattern());

        return new Declarables(
                questionsExchange, questionsDlx, answersExchange,
                questionsQueue, questionsDlq, answersQueue,
                questionsBinding, dlqBinding, answersBinding);
    }
}
