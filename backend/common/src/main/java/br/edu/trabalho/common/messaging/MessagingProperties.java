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
        String answersRoutingKeyPrefix
) {

    /**
     * Routing key da resposta, específica do usuário: {@code respostas.<userId>} (ex.: respostas.aluno).
     */
    public String answersRoutingKeyFor(String userId) {
        return answersRoutingKeyPrefix + "." + userId;
    }

    /**
     * Padrão do binding de {@code respostas.queue} na exchange topic: {@code respostas.#}
     * ({@code #} casa qualquer sequência de palavras, logo a resposta de qualquer usuário).
     */
    public String answersBindingPattern() {
        return answersRoutingKeyPrefix + ".#";
    }
}
