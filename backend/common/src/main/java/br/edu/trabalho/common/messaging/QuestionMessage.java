package br.edu.trabalho.common.messaging;

import java.time.Instant;

/**
 * Payload publicado pelo Gateway em {@code perguntas.queue}.
 *
 * @param correlationId identificador único da pergunta; acompanha a resposta de volta
 * @param userId        usuário autenticado (subject do JWT) que fez a pergunta
 * @param question      texto original digitado pelo usuário
 * @param createdAt     instante em que o Gateway aceitou a pergunta
 */
public record QuestionMessage(
        String correlationId,
        String userId,
        String question,
        Instant createdAt
) {
}
