package br.edu.trabalho.common.messaging;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Nomes de exchanges, filas e routing keys (prefixo {@code app.messaging} no application.yml).
 */
@ConfigurationProperties(prefix = "app.messaging")
public record MessagingProperties(
        String questionsExchange,
        String questionsQueue,
        String questionsRoutingKey,
        String questionsDeadLetterExchange,
        String questionsDeadLetterQueue,
        String answersExchange,
        String answersQueue,
        String answersRoutingKey
) {
}
